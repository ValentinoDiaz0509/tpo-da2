# AWS Architecture Diagram — Módulo 9

Rendered architecture diagram using the **official, colored AWS service icons**.

![M9 AWS Architecture](./m9-aws-architecture.png)

- `m9-aws-architecture.png` — raster image for embedding in docs/slides.
- `m9-aws-architecture.svg` — vector version (crisp at any zoom).
- `m9_architecture.py` — source of truth. Edit this and re-render; do not hand-edit the images.

## What it shows

Sourced from [`../BIG_PICTURE_ARCHITECTURE.md`](../BIG_PICTURE_ARCHITECTURE.md) and
[`../aws-deployment-guide.md`](../aws-deployment-guide.md):

- **Frontend:** Route 53 → CloudFront → S3 bucket serving the React SPA (`appvalen`) static Vite build (private bucket via Origin Access Control)
- **Edge / API:** Route 53 → ACM/ALB (HTTPS 443 + WebSocket passthrough) for the backend
- **Compute:** ECS Fargate task running the Spring Boot service (REST, STOMP/WS, rule engine, SQS consumers); image pulled from ECR
- **Data:** RDS PostgreSQL (patients/rules/alerts/users) + DynamoDB (telemetry, TTL 90d)
- **Messaging:** SQS ingest (telemetry, patient-events) and SNS fan-out → SQS m6/m8 subscribers
- **Ops:** Secrets Manager, CloudWatch
- **External:** IoT sensors, M6 Internación, M8 Portal, M10 Core (JWT issuer)

## Re-rendering

Requires the Graphviz `dot` binary and the Python `diagrams` library:

```bash
sudo apt-get install -y graphviz          # system dependency (dot)
python3 -m venv venv && ./venv/bin/pip install diagrams

cd docs/architecture/diagrams
../../../venv/bin/python m9_architecture.py   # writes the .png and .svg
```
