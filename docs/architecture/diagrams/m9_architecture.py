"""
Módulo 9 — Patient Monitoring System
AWS architecture diagram rendered with official, colored AWS service icons
(via the mingrammer `diagrams` library).

Source of truth: infrastructure/cdk/lib/*.ts (the deployed CDK stacks).
This diagram is kept in sync with what is actually provisioned there:
  - M9Backend   (m9-backend-stack.ts):  VPC, ALB, ECS Fargate, RDS, SQS, Secrets, CW, ECR
  - M9Frontend  (m9-frontend-stack.ts): S3 (private/OAC) + CloudFront (also /api/* proxy)
  - GithubOidc  (github-oidc-stack.ts): GitHub Actions OIDC deploy role (CI, not runtime)
"""

import os

from diagrams import Diagram, Cluster, Edge

from diagrams.aws.network import ElbApplicationLoadBalancer, CloudFront
from diagrams.aws.security import SecretsManager
from diagrams.aws.compute import ECS, EC2ContainerRegistry
from diagrams.aws.storage import S3
from diagrams.aws.database import RDSPostgresqlInstance
from diagrams.aws.integration import SimpleQueueServiceSqs
from diagrams.aws.management import Cloudwatch
from diagrams.aws.iot import IotSensor
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
    "label": "Módulo 9 — Hospital Patient Monitoring · AWS us-east-1",
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
    # ── External actors & sibling modules ───────────────────────────
    nurse = Users("Nurse / Physician\n(Web Browser)")
    iot = IotSensor("IoT Vital-Signs Sensors\nPhilips IntelliVue · GE")

    with Cluster("External Modules"):
        m10 = Client("M10 — Core\n(JWT Issuer)")
        m6 = Client("M6 — Internación\n(Admission)")

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
            backend = Spring("Spring Boot 3.3\nREST + STOMP/WS\n+ Rule Engine\n+ SCS SQS consumers")

    ecr = EC2ContainerRegistry("ECR\nhealth-grid/m9-monitoring")

    # ── Data store (single RDS — no DynamoDB is provisioned) ────────
    with Cluster("Data (public subnet · SG-locked to ECS)"):
        rds = RDSPostgresqlInstance(
            "RDS PostgreSQL 16.4\npatients · rules · alerts\ntelemetry_readings · processed_messages"
        )

    # ── Messaging (SQS only — each queue has a DLQ, maxReceive 3) ────
    with Cluster("Messaging · SQS (+ DLQ each)"):
        sqs_t = SimpleQueueServiceSqs("SQS telemetry-\nreadings-queue")
        sqs_p = SimpleQueueServiceSqs("SQS patient-\nevents-queue")
        sqs_a = SimpleQueueServiceSqs("SQS admission-\nevents-queue")

    # ── Ops ─────────────────────────────────────────────────────────
    secrets = SecretsManager("Secrets Manager\nm9/db-credentials\nm9/jwt-secret")
    cw = Cloudwatch("CloudWatch Logs\n/ecs/m9-monitoring")

    # ── Flows: user → CloudFront → S3 (SPA) and → ALB (/api/*) ──────
    nurse >> Edge(label="HTTPS") >> cf
    cf >> Edge(label="default behavior\nOAC private read") >> s3
    cf >> Edge(label="/api/* proxy (HTTP)\nREST + WS") >> alb
    alb >> Edge(label="REST + STOMP/WS\nBearer JWT") >> backend

    # ── Flows: IoT & admission ingest ───────────────────────────────
    iot >> Edge(label="JSON telemetry") >> sqs_t >> Edge(label="long-poll") >> backend
    m6 >> Edge(label="patient events") >> sqs_p >> backend
    m6 >> Edge(label="admission events") >> sqs_a >> backend
    m6 >> Edge(style="dashed", label="webhook /webhook/admission") >> backend

    # ── Flows: persistence ──────────────────────────────────────────
    backend >> Edge(label="JPA (Hibernate)") >> rds

    # ── Flows: alert fan-out (WebSocket + HTTP webhook, no SNS) ──────
    backend >> Edge(style="dashed", label="WebSocket push\n/topic/monitoring/{id}") >> alb
    backend >> Edge(style="dashed", label="emergency webhook\nMODULE6_WEBHOOK_URL (HTTP)") >> m6

    # ── Flows: auth & ops ───────────────────────────────────────────
    m10 >> Edge(style="dotted", label="Future: real JWT\n(JWKS / RS256)") >> backend
    ecs >> Edge(style="dotted") >> backend
    ecr >> Edge(style="dotted", label="image pull") >> backend
    backend >> Edge(style="dotted", label="read secrets") >> secrets
    backend >> Edge(style="dotted", label="logs") >> cw

# Graphviz leaves the intermediate DOT source (an extensionless file next to the
# images) behind. Remove it so only the .png/.svg remain.
if os.path.exists(OUT_BASENAME):
    os.remove(OUT_BASENAME)
