"""
Módulo 9 — Patient Monitoring System
AWS architecture diagram rendered with official, colored AWS service icons
(via the mingrammer `diagrams` library).

Source of truth: docs/architecture/BIG_PICTURE_ARCHITECTURE.md
                 docs/architecture/aws-deployment-guide.md
"""

from diagrams import Diagram, Cluster, Edge

from diagrams.aws.network import Route53, ElbApplicationLoadBalancer, CloudFront
from diagrams.aws.security import CertificateManager, SecretsManager
from diagrams.aws.compute import ECS, EC2ContainerRegistry
from diagrams.aws.storage import S3
from diagrams.aws.database import RDSPostgresqlInstance, Dynamodb
from diagrams.aws.integration import SimpleQueueServiceSqs, SimpleNotificationServiceSns
from diagrams.aws.management import Cloudwatch
from diagrams.aws.iot import IotSensor
from diagrams.onprem.client import Users, Client
from diagrams.programming.framework import Spring

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
    filename="m9-aws-architecture",
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
        m2 = Client("M2 — Patient Portal")

    # ── Edge / DNS / CDN ────────────────────────────────────────────
    with Cluster("Edge"):
        dns = Route53("Route 53")
        acm = CertificateManager("ACM TLS cert\n(us-east-1)")
        cf = CloudFront("CloudFront\nCDN · HTTPS")
        alb = ElbApplicationLoadBalancer("Application Load Balancer\nHTTPS 443 · WebSocket")

    # ── Frontend hosting (static SPA) ───────────────────────────────
    with Cluster("Frontend hosting (static)"):
        s3 = S3("S3 Bucket\nReact 19 SPA (appvalen)\nVite build · private (OAC)")

    # ── Compute (ECS Fargate) ───────────────────────────────────────
    with Cluster("ECS Fargate · health-grid cluster"):
        ecs = ECS("ECS Service\ndesired 2 · autoscale 2–6")
        with Cluster("Task: m9-monitoring (port 8080 · /api/v1)"):
            backend = Spring("Spring Boot 3.3\nREST + STOMP/WS\n+ Rule Engine\n+ SCS SQS consumers")

    ecr = EC2ContainerRegistry("ECR\nhealth-grid/m9-monitoring")

    # ── Data stores ─────────────────────────────────────────────────
    with Cluster("Data (private subnet)"):
        rds = RDSPostgresqlInstance("RDS PostgreSQL 16\npatients · rules\nalerts · users")
        ddb = Dynamodb("DynamoDB\nm9-telemetry-readings\nTTL 90d")

    # ── Messaging ───────────────────────────────────────────────────
    with Cluster("Messaging"):
        sqs_t = SimpleQueueServiceSqs("SQS telemetry-\nreadings-queue")
        sqs_p = SimpleQueueServiceSqs("SQS patient-\nevents-queue")
        sns = SimpleNotificationServiceSns("SNS\nmonitoring-events")
        sqs_m6 = SimpleQueueServiceSqs("SQS m6-\nmonitoring-sub")
        sqs_m2 = SimpleQueueServiceSqs("SQS m2-\nmonitoring-sub")

    # ── Ops ─────────────────────────────────────────────────────────
    secrets = SecretsManager("Secrets Manager\nDB creds · JWT secret")
    cw = Cloudwatch("CloudWatch\nLogs + Metrics")

    # ── Flows: user → frontend (S3+CloudFront) & backend (ALB) ──────
    nurse >> Edge(label="HTTPS") >> dns
    dns >> Edge(label="dashboard host") >> cf >> Edge(label="OAC private read") >> s3
    dns >> Edge(label="api host") >> alb
    acm >> Edge(style="dotted", label="TLS") >> cf
    acm >> Edge(style="dotted", label="TLS") >> alb
    alb >> Edge(label="REST + STOMP/WS\nBearer JWT") >> backend

    # ── Flows: IoT & admission ingest ───────────────────────────────
    iot >> Edge(label="JSON telemetry") >> sqs_t >> Edge(label="long-poll") >> backend
    m6 >> Edge(label="admission events") >> sqs_p >> backend
    m6 >> Edge(style="dashed", label="webhook /webhook/admission") >> backend

    # ── Flows: persistence ──────────────────────────────────────────
    backend >> Edge(label="JPA") >> rds
    backend >> Edge(label="Enhanced client\nQuery last 10 min") >> ddb

    # ── Flows: alert fan-out ────────────────────────────────────────
    backend >> Edge(style="dashed", label="WebSocket push\n/topic/monitoring/{id}") >> alb
    backend >> Edge(label="publish") >> sns
    sns >> sqs_m6 >> m6
    sns >> sqs_m2 >> m2

    # ── Flows: auth & ops ───────────────────────────────────────────
    m10 >> Edge(style="dotted", label="Future: real JWT\n(JWKS / RS256)") >> backend
    ecs >> Edge(style="dotted") >> backend
    ecr >> Edge(style="dotted", label="image pull") >> backend
    backend >> Edge(style="dotted", label="read secrets") >> secrets
    backend >> Edge(style="dotted", label="logs / metrics") >> cw
