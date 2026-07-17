"""
Módulo 9 — Patient Monitoring System
Architecture diagram rendered with official, colored service icons
(via the mingrammer `diagrams` library).

Source of truth: the running code + infrastructure/cdk/lib/*.ts (the deployed CDK stacks).
This diagram reflects what the service actually does today:
  - Compute/edge (CDK): S3 + CloudFront (SPA, /api/* proxy), ALB, ECS Fargate, RDS, Secrets, CW, ECR
  - Messaging: the Module 10 (Core) RabbitMQ event bus — NOT AWS SQS/SNS.
      · inbound  : @RabbitListener on `monitoring.requests` (alta/baja monitoreo from M6)
      · outbound : POST /events/log to the Core (alert ids 16/17 → M6's internacion.requests)
  - Telemetry is INTERNAL (simulator / in-process) — it does not traverse any broker.
  - Persistence: a single RDS PostgreSQL (patients, rules, alerts, telemetry_readings,
    processed_messages) — there is no DynamoDB.

Infra follow-up: the CDK still provisions three now-unused SQS queues + AWS_SQS_* env vars,
pending removal in favour of RABBITMQ_* / MODULE10_CORE_* variables.
"""

import os

from diagrams import Diagram, Cluster, Edge

from diagrams.aws.network import ElbApplicationLoadBalancer, CloudFront
from diagrams.aws.security import SecretsManager
from diagrams.aws.compute import ECS, EC2ContainerRegistry
from diagrams.aws.storage import S3
from diagrams.aws.database import RDSPostgresqlInstance
from diagrams.aws.management import Cloudwatch
from diagrams.aws.iot import IotSensor
from diagrams.onprem.queue import RabbitMQ
from diagrams.onprem.client import Users, Client
from diagrams.programming.framework import Spring

# Render next to this script regardless of the current working directory, so the
# committed PNG/SVG are always the ones that get updated (running from the repo
# root previously wrote the images into the repo root instead of here).
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_BASENAME = os.path.join(HERE, "m9-aws-architecture")

graph_attr = {
    "fontsize": "22",
    "fontname": "Helvetica",
    "labelloc": "t",
    "label": "Módulo 9 — Hospital Patient Monitoring · AWS us-east-1 + Core Event Bus",
    "bgcolor": "white",
    "pad": "0.6",
    "nodesep": "0.6",
    "ranksep": "1.0",
    "splines": "spline",
}

node_attr = {"fontname": "Helvetica", "fontsize": "11"}

with Diagram(
    "M9 Patient Monitoring",
    filename=OUT_BASENAME,
    outformat=["png", "svg"],
    show=False,
    direction="TB",
    graph_attr=graph_attr,
    node_attr=node_attr,
):
    # ── External actors ─────────────────────────────────────────────
    nurse = Users("Nurse / Physician\n(Web Browser)")
    iot = IotSensor("IoT Vital-Signs Sensors\nPhilips IntelliVue · GE")

    # ── Module 10 (Core): identity + RabbitMQ event bus ─────────────
    with Cluster("Module 10 — Core (external)"):
        m10 = Client("Core API\napi.healthcare.cantero.ar\nJWT (JWKS) · POST /events/log")
        rabbit = RabbitMQ(
            "Core Event Bus\nhealth_grid_exchange (topic)\nqueue.healthgrid.cantero.ar"
        )

    with Cluster("Module 6 — Internación"):
        m6 = Client("M6 — Internación\n(alta/baja monitoreo\n+ alert consumer)")

    # ── Frontend edge (M9Frontend stack) ────────────────────────────
    with Cluster("M9Frontend · S3 + CloudFront"):
        cf = CloudFront("CloudFront\nCDN · HTTPS (default cert)\ndefault → S3 · /api/* → ALB")
        s3 = S3("S3 Bucket\nReact 19 SPA (appvalen)\nVite build · private (OAC)")

    # ── Backend edge (M9Backend stack) ──────────────────────────────
    alb = ElbApplicationLoadBalancer(
        "Application Load Balancer\nHTTP :80 · sticky · WebSocket"
    )

    # ── Compute (ECS Fargate) ───────────────────────────────────────
    with Cluster("ECS Fargate · health-grid cluster"):
        ecs = ECS("ECS Service\ndesired 1 · FARGATE_SPOT")
        with Cluster("Task: m9-monitoring (0.25 vCPU / 1 GB · 8080 · /api/v1)"):
            backend = Spring(
                "Spring Boot 3.3\nREST + STOMP/WS\n+ Rule Engine\n+ RabbitMQ listener"
            )

    ecr = EC2ContainerRegistry("ECR\nhealth-grid/m9-monitoring")

    # ── Data store (single RDS — no DynamoDB is provisioned) ────────
    with Cluster("Data (public subnet · SG-locked to ECS)"):
        rds = RDSPostgresqlInstance(
            "RDS PostgreSQL 16.4\npatients · rules · alerts\ntelemetry_readings · processed_messages"
        )

    # ── Ops ─────────────────────────────────────────────────────────
    secrets = SecretsManager("Secrets Manager\nm9/db-credentials\nm9/jwt-secret")
    cw = Cloudwatch("CloudWatch Logs\n/ecs/m9-monitoring")

    # ── Flows: user → CloudFront → S3 (SPA) and → ALB (/api/*) ──────
    nurse >> Edge(label="HTTPS") >> cf
    cf >> Edge(label="default behavior\nOAC private read") >> s3
    cf >> Edge(label="/api/* proxy (HTTP)\nREST + WS") >> alb
    alb >> Edge(label="REST + STOMP/WS\nBearer JWT") >> backend

    # ── Flows: telemetry is internal (simulator / in-process) ───────
    iot >> Edge(style="dashed", label="in-process ingestion\n(no broker)") >> backend

    # ── Flows: admission IN via Core bus (+ legacy webhook) ─────────
    m6 >> Edge(label="POST /events/log\n(alta/baja monitoreo)") >> m10
    m10 >> Edge(label="route to exchange") >> rabbit
    rabbit >> Edge(label="monitoring.requests\n@RabbitListener") >> backend
    m6 >> Edge(style="dashed", label="legacy webhook\n/webhooks/internacion/*") >> backend

    # ── Flows: persistence ──────────────────────────────────────────
    backend >> Edge(label="JPA (Hibernate)") >> rds

    # ── Flows: alert fan-out (WebSocket + Core bus + legacy webhook) ─
    backend >> Edge(style="dashed", label="WebSocket push\n/topic/monitoring/{id}") >> alb
    backend >> Edge(label="publish alerts\nPOST /events/log id 16/17") >> m10
    rabbit >> Edge(label="internacion.requests") >> m6
    backend >> Edge(style="dashed", label="legacy emergency webhook\nMODULE6_WEBHOOK_URL (HTTP)") >> m6

    # ── Flows: auth & ops ───────────────────────────────────────────
    m10 >> Edge(style="dotted", label="JWT validation\n(JWKS / RS256)") >> backend
    ecs >> Edge(style="dotted") >> backend
    ecr >> Edge(style="dotted", label="image pull") >> backend
    backend >> Edge(style="dotted", label="read secrets") >> secrets
    backend >> Edge(style="dotted", label="logs") >> cw

# Graphviz leaves the intermediate DOT source (an extensionless file next to the
# images) behind. Remove it so only the .png/.svg remain.
if os.path.exists(OUT_BASENAME):
    os.remove(OUT_BASENAME)
