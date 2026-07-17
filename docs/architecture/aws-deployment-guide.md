# AWS Deployment Guide — Módulo 9: Monitoreo de Pacientes

> **Stack:** Spring Boot 3.3 / Java 17 backend (`monitoring-service`) + React 19 / Vite frontend. Persistence is split between **RDS PostgreSQL** (relational: patients, rules, alerts) and **DynamoDB** (time-series telemetry readings).

## Architecture Decision: ECS Fargate vs Lambda

### Why NOT Lambda for M9

Lambda seems appealing (no server management, pay-per-invocation), but M9 has characteristics that make it a poor fit:

| Concern | Lambda Limitation | M9 Requirement |
|---------|-------------------|----------------|
| **WebSocket** | Lambda cannot hold persistent connections. API Gateway WebSocket API exists but adds significant complexity and cost for high-frequency telemetry updates. | Nursing dashboard needs real-time push via STOMP over WebSocket (SockJS fallback) at `/api/v1/ws`. |
| **Cold starts** | JVM cold starts on Lambda (1.5–6s for a Spring Boot fat jar without SnapStart) are unacceptable for a patient monitoring system where alerts must fire in near real-time. | Rule engine must evaluate telemetry immediately on arrival. |
| **Execution time** | 15-minute max timeout. | Spring Cloud Stream SQS binder needs to poll continuously. |
| **Concurrency** | Each invocation is isolated — no shared in-memory state across requests. | Spring app holds connection state, STOMP sessions, HikariCP pool, and the cached active-rule set. |

### Recommended: ECS Fargate (Primary) + Lambda (Event Handlers)

Use **ECS Fargate** for the main Spring Boot application and optionally **Lambda** for isolated event-driven tasks.

| Component | Compute | Reason |
|-----------|---------|--------|
| Spring Boot REST API + WebSocket | **ECS Fargate** | Long-running JVM, persistent STOMP connections, warm HikariCP pool |
| SQS telemetry consumer (Spring Cloud Stream) | **ECS Fargate** (same task) | Continuous polling, shares process with rule engine |
| SNS → SQS dead-letter reprocessing | **Lambda** (optional) | Infrequent, event-driven, stateless |

---

## Target Architecture

```
                    ┌─────────────────┐
                    │   Route 53      │
                    │  (DNS)          │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  ALB            │
                    │  (Application   │
                    │   Load Balancer)│
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  ECS Fargate    │
                    │  ┌────────────┐ │
                    │  │ Spring     │ │──── POST /api/v1/telemetry
                    │  │ Boot 3.3   │ │──── GET  /api/v1/patients
                    │  │ (JVM 17)   │ │──── GET  /api/v1/alerts
                    │  │ + STOMP/WS │ │──── WS   /api/v1/ws
                    │  │ + SCS SQS  │ │           /topic/monitoring/{id}
                    │  └─────┬──────┘ │
                    └────────┼────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       │                     │                     │
┌──────▼──────┐      ┌───────▼──────┐      ┌───────▼──────┐
│ RDS         │      │ DynamoDB     │      │ SNS Topic    │
│ PostgreSQL  │      │ telemetry-   │      │ monitoring   │
│ (patients,  │      │ readings     │      │ -events      │
│  rules,     │      │ (time-series)│      └───────┬──────┘
│  alerts)    │      └──────────────┘              │
└─────────────┘                                    │
                                         ┌─────────┼─────────┐
                                         │                   │
                                 ┌───────▼──────┐  ┌────────▼─────┐
                                 │ SQS Queue    │  │ SQS Queue    │
                                 │ m6-sub       │  │ m8-sub       │
                                 │ (Internación)│  │ (Portal)     │
                                 └──────────────┘  └──────────────┘

                    ┌─────────────────────────────────┐
                    │ SQS Queue: telemetry-readings   │  (ingest from IoT)
                    │ SQS Queue: patient-events       │  (admission events)
                    └────────────────┬────────────────┘
                                     │
                              consumed by Spring
                              Cloud Stream binders
```

### Why Postgres + DynamoDB (polyglot persistence)?

| Data | Store | Reason |
|------|-------|--------|
| Patients, rules, alerts, users | **RDS PostgreSQL** | Relational, low write rate, strong consistency, complex queries (filter by status, severity, room) |
| Telemetry readings (vital signs every few seconds per patient) | **DynamoDB** | High write throughput, predictable single-key access (`patient_id` + `recorded_at`), TTL for auto-purge, no schema migrations as new metrics are added |

The rule engine queries the most recent N readings per patient for sustained-violation evaluation — this is a single-partition `Query` in DynamoDB, cheaper and faster than time-windowed scans against a growing Postgres table.

---

## Step-by-Step Deployment

### Step 1: Containerize the Application

Create a `Dockerfile` in the `backend/` directory. Multi-stage build keeps the runtime image small:

```dockerfile
# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy sources and package
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Non-root user
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /workspace/target/monitoring-service-1.0.0.jar app.jar

EXPOSE 8080

# Health check hits Spring Boot Actuator (context path /api/v1)
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/v1/actuator/health || exit 1

# JVM flags tuned for containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

Build and test locally:

```bash
docker build -t m9-monitoring ./backend
docker run -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SIMULATOR_ENABLED=false \
    m9-monitoring
```

### Step 2: Push Image to ECR

```bash
# Create ECR repository
aws ecr create-repository --repository-name health-grid/m9-monitoring

# Authenticate Docker with ECR
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Tag and push
docker tag m9-monitoring:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/health-grid/m9-monitoring:latest
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/health-grid/m9-monitoring:latest
```

### Step 3: Create the Relational Database (RDS PostgreSQL)

```bash
aws rds create-db-instance \
    --db-instance-identifier m9-monitoring-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 16.4 \
    --master-username m9admin \
    --master-user-password <SECURE_PASSWORD> \
    --allocated-storage 20 \
    --vpc-security-group-ids <SG_ID> \
    --db-subnet-group-name <SUBNET_GROUP> \
    --no-publicly-accessible \
    --storage-encrypted
```

Store credentials in Secrets Manager. Spring Boot will read a single JDBC URL from this secret:

```bash
aws secretsmanager create-secret \
    --name health-grid/m9/db-credentials \
    --secret-string '{
        "spring.datasource.url":"jdbc:postgresql://<RDS_ENDPOINT>:5432/m9monitoring",
        "spring.datasource.username":"m9admin",
        "spring.datasource.password":"<SECURE_PASSWORD>"
    }'
```

> The Spring Boot app reads these via `SPRING_DATASOURCE_*` env vars injected from Secrets Manager — see the task definition below.

### Step 4: Create the Time-Series Store (DynamoDB)

Telemetry readings (vital signs at ~1 Hz per patient) are stored in DynamoDB rather than Postgres. Composite key keeps each patient's history in a single partition for fast range queries; TTL purges old readings automatically.

```bash
aws dynamodb create-table \
    --table-name m9-telemetry-readings \
    --attribute-definitions \
        AttributeName=patient_id,AttributeType=S \
        AttributeName=recorded_at,AttributeType=S \
    --key-schema \
        AttributeName=patient_id,KeyType=HASH \
        AttributeName=recorded_at,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --sse-specification Enabled=true \
    --tags Key=Project,Value=health-grid Key=Module,Value=m9

# Enable TTL on a numeric epoch attribute so old readings auto-purge (e.g. after 90 days)
aws dynamodb update-time-to-live \
    --table-name m9-telemetry-readings \
    --time-to-live-specification "Enabled=true, AttributeName=expires_at"

# Enable PITR (point-in-time recovery) for compliance
aws dynamodb update-continuous-backups \
    --table-name m9-telemetry-readings \
    --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true
```

**Item shape** (written by the Spring Boot app via the AWS SDK v2 Enhanced DynamoDB client):

```json
{
  "patient_id":     "550e8400-e29b-41d4-a716-446655440000",
  "recorded_at":    "2026-05-17T14:32:05.412Z",
  "heart_rate":     85.5,
  "spo2":           98.5,
  "systolic":       120.0,
  "diastolic":      80.0,
  "temperature":    37.2,
  "sensor_id":      "SENSOR-ICU-001",
  "unit_id":        "UNIT-001",
  "expires_at":     1762177925
}
```

> **What lives where:** `Patient`, `Rule`, `Alert`, and user data stay in Postgres (relational, low write rate, complex queries). Only `TelemetryReading` moves to DynamoDB. The `RuleEngineService` lookback window (last 10 minutes per patient) becomes a single `Query` with `ScanIndexForward=false` and `Limit=N`.

### Step 5: Create SNS Topics and SQS Queues

```bash
# SNS topic for emergency events
aws sns create-topic --name monitoring-events
# Save the TopicArn from the output

# SQS queues for subscribers
aws sqs create-queue --queue-name m6-monitoring-sub \
    --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"60"}'

aws sqs create-queue --queue-name m8-monitoring-sub \
    --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"60"}'

# Dead-letter queues
aws sqs create-queue --queue-name m6-monitoring-sub-dlq
aws sqs create-queue --queue-name m8-monitoring-sub-dlq

# Subscribe SQS queues to SNS topic
aws sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:<ACCOUNT_ID>:monitoring-events \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-east-1:<ACCOUNT_ID>:m6-monitoring-sub

aws sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:<ACCOUNT_ID>:monitoring-events \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-east-1:<ACCOUNT_ID>:m8-monitoring-sub

# Optional: telemetry ingestion queue (if devices publish to SQS instead of REST)
aws sqs create-queue --queue-name m9-telemetry-ingest \
    --attributes '{"MessageRetentionPeriod":"3600","VisibilityTimeout":"30"}'
```

### Step 6: Create the ECS Cluster and Task Definition

```bash
# Create cluster
aws ecs create-cluster --cluster-name health-grid --capacity-providers FARGATE

# Register task definition (see JSON below)
aws ecs register-task-definition --cli-input-json file://task-definition.json
```

**task-definition.json:**

```json
{
  "family": "m9-monitoring",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/m9-monitoring-task-role",
  "containerDefinitions": [
    {
      "name": "m9-monitoring",
      "image": "<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/health-grid/m9-monitoring:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
        { "name": "SERVER_PORT", "value": "8080" },
        { "name": "AWS_REGION", "value": "us-east-1" },
        { "name": "SIMULATOR_ENABLED", "value": "false" },
        { "name": "SNS_TOPIC_ARN", "value": "arn:aws:sns:us-east-1:<ACCOUNT_ID>:monitoring-events" },
        { "name": "SQS_TELEMETRY_QUEUE", "value": "m9-telemetry-ingest" },
        { "name": "SQS_PATIENT_EVENTS_QUEUE", "value": "patient-events-queue" },
        { "name": "DYNAMODB_TELEMETRY_TABLE", "value": "m9-telemetry-readings" },
        { "name": "MODULE6_WEBHOOK_URL", "value": "https://internacion.healthgrid.com/webhooks/m9-alerts" },
        { "name": "MODULE10_CORE_URL", "value": "https://api.healthcare.cantero.ar" },
        { "name": "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "value": "health,info,metrics,prometheus" }
      ],
      "secrets": [
        {
          "name": "SPRING_DATASOURCE_URL",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:health-grid/m9/db-credentials:spring.datasource.url::"
        },
        {
          "name": "SPRING_DATASOURCE_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:health-grid/m9/db-credentials:spring.datasource.username::"
        },
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:health-grid/m9/db-credentials:spring.datasource.password::"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:8080/api/v1/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/m9-monitoring",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "m9"
        }
      }
    }
  ]
}
```

### Step 7: Create the Application Load Balancer

```bash
# Create ALB
aws elbv2 create-load-balancer \
    --name m9-monitoring-alb \
    --subnets <SUBNET_1> <SUBNET_2> \
    --security-groups <ALB_SG_ID> \
    --scheme internet-facing \
    --type application

# Create target group (supports HTTP and WebSocket)
aws elbv2 create-target-group \
    --name m9-monitoring-tg \
    --protocol HTTP \
    --port 8080 \
    --vpc-id <VPC_ID> \
    --target-type ip \
    --health-check-path /api/v1/actuator/health \
    --health-check-interval-seconds 30 \
    --health-check-timeout-seconds 5 \
    --healthy-threshold-count 2

# Enable stickiness so SockJS fallback transports (XHR-streaming) stay pinned to one task
aws elbv2 modify-target-group-attributes \
    --target-group-arn <TG_ARN> \
    --attributes \
        Key=stickiness.enabled,Value=true \
        Key=stickiness.type,Value=lb_cookie \
        Key=stickiness.lb_cookie.duration_seconds,Value=3600

# Create listener (HTTPS — requires ACM certificate)
aws elbv2 create-listener \
    --load-balancer-arn <ALB_ARN> \
    --protocol HTTPS \
    --port 443 \
    --certificates CertificateArn=<ACM_CERT_ARN> \
    --default-actions Type=forward,TargetGroupArn=<TG_ARN>
```

> **Note:** ALB natively supports WebSocket connections. No special configuration needed — WebSocket upgrades pass through automatically. Stickiness is only required for SockJS HTTP-streaming fallback when native WebSocket isn't available.

### Step 8: Create the ECS Service

```bash
aws ecs create-service \
    --cluster health-grid \
    --service-name m9-monitoring \
    --task-definition m9-monitoring \
    --desired-count 2 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[<SUBNET_1>,<SUBNET_2>],securityGroups=[<SG_ID>],assignPublicIp=DISABLED}" \
    --load-balancers "targetGroupArn=<TG_ARN>,containerName=m9-monitoring,containerPort=8080" \
    --health-check-grace-period-seconds 90
```

> Grace period bumped to 90s to absorb JVM warm-up before the first health check.

### Step 9: Configure Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --scalable-dimension ecs:service:DesiredCount \
    --resource-id service/health-grid/m9-monitoring \
    --min-capacity 2 \
    --max-capacity 6

# Scale on CPU utilization
aws application-autoscaling put-scaling-policy \
    --service-namespace ecs \
    --scalable-dimension ecs:service:DesiredCount \
    --resource-id service/health-grid/m9-monitoring \
    --policy-name m9-cpu-scaling \
    --policy-type TargetTrackingScaling \
    --target-tracking-scaling-policy-configuration '{
        "TargetValue": 70.0,
        "PredefinedMetricSpecification": {
            "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
        },
        "ScaleInCooldown": 300,
        "ScaleOutCooldown": 60
    }'
```

---

## IAM Roles

### ECS Task Role (`m9-monitoring-task-role`)

The application container needs permissions to interact with AWS services:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SNSPublish",
      "Effect": "Allow",
      "Action": "sns:Publish",
      "Resource": "arn:aws:sns:us-east-1:<ACCOUNT_ID>:monitoring-events"
    },
    {
      "Sid": "SQSConsume",
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": [
        "arn:aws:sqs:us-east-1:<ACCOUNT_ID>:m9-telemetry-ingest",
        "arn:aws:sqs:us-east-1:<ACCOUNT_ID>:patient-events-queue"
      ]
    },
    {
      "Sid": "DynamoDBTelemetry",
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:BatchWriteItem",
        "dynamodb:GetItem",
        "dynamodb:Query",
        "dynamodb:DescribeTable"
      ],
      "Resource": "arn:aws:dynamodb:us-east-1:<ACCOUNT_ID>:table/m9-telemetry-readings"
    },
    {
      "Sid": "SecretsRead",
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:health-grid/m9/*"
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:us-east-1:<ACCOUNT_ID>:log-group:/ecs/m9-monitoring:*"
    }
  ]
}
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `SERVER_PORT` | Tomcat port (matches ALB target) | `8080` |
| `SPRING_DATASOURCE_URL` | JDBC URL (from Secrets Manager) | `jdbc:postgresql://host:5432/m9monitoring` |
| `SPRING_DATASOURCE_USERNAME` | DB user (from Secrets Manager) | `m9admin` |
| `SPRING_DATASOURCE_PASSWORD` | DB password (from Secrets Manager) | `••••••` |
| `DYNAMODB_TELEMETRY_TABLE` | DynamoDB table for telemetry readings | `m9-telemetry-readings` |
| `SNS_TOPIC_ARN` | ARN for the monitoring-events SNS topic | `arn:aws:sns:us-east-1:123456:monitoring-events` |
| `SQS_TELEMETRY_QUEUE` | Queue name for telemetry ingestion | `m9-telemetry-ingest` |
| `SQS_PATIENT_EVENTS_QUEUE` | Queue name for patient admission events | `patient-events-queue` |
| `MODULE6_WEBHOOK_URL` | Internación module webhook receiver | `https://internacion.healthgrid.com/webhooks/m9-alerts` |
| `AWS_REGION` | AWS region (used by the SDK v2) | `us-east-1` |
| `SIMULATOR_ENABLED` | Toggles the in-process telemetry simulator — **must be `false` in prod** | `false` |
| `MODULE10_CORE_URL` | Core base URL for service login and JWKS validation | `https://api.healthcare.cantero.ar` |
| `ALLOWED_ORIGINS` | CORS origins for WebSocket/dashboard | `https://dashboard.healthgrid.com` |

---

## Security Checklist

- [ ] RDS is in a private subnet, not publicly accessible
- [ ] ECS tasks run in private subnets, only ALB is internet-facing
- [ ] All traffic is HTTPS (ACM certificate on ALB)
- [ ] Security groups: ALB allows 443 inbound; ECS allows 8000 only from ALB SG; RDS allows 5432 only from ECS SG
- [ ] Secrets stored in AWS Secrets Manager, not environment variables
- [ ] Task role follows least-privilege principle
- [ ] CloudWatch alarms configured for error rates and latency

---

## CI/CD Pipeline (GitHub Actions)

A basic pipeline to build, push, and deploy on every push to `main`:

```yaml
# .github/workflows/deploy.yml
name: Deploy M9 to ECS

on:
  push:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: health-grid/m9-monitoring
  ECS_CLUSTER: health-grid
  ECS_SERVICE: m9-monitoring

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      id-token: write
      contents: read

    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::<ACCOUNT_ID>:role/github-actions-deploy
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: ecr-login
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build, tag, and push image
        env:
          ECR_REGISTRY: ${{ steps.ecr-login.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG ./backend
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster $ECS_CLUSTER \
            --service $ECS_SERVICE \
            --force-new-deployment
```

---

## Estimated Costs (us-east-1, minimal setup)

| Service | Configuration | Monthly Estimate |
|---------|--------------|-----------------|
| ECS Fargate | 2 tasks × 0.5 vCPU / 1 GB | ~$30 |
| RDS PostgreSQL | db.t3.micro, 20 GB | ~$15 |
| DynamoDB | On-demand, ~3M writes + 1M reads, 5 GB storage | ~$5 |
| ALB | 1 ALB + LCU hours | ~$20 |
| SNS | < 1M publishes | ~$0.50 |
| SQS | < 1M requests | ~$0.40 |
| CloudWatch Logs | 5 GB ingestion | ~$2.50 |
| ECR | < 1 GB storage | ~$0.10 |
| **Total** | | **~$75/month** |

> These are estimates for a development/staging environment. Production with higher availability (multi-AZ RDS, more Fargate tasks) would be higher.

---

## Local Development with LocalStack

For local development without an AWS account, use [LocalStack](https://localstack.cloud) to emulate SNS, SQS, and DynamoDB:

```yaml
# docker-compose.yml
services:
  app:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/m9monitoring
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - AWS_ENDPOINT_URL=http://localstack:4566
      - SNS_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:monitoring-events
      - SQS_TELEMETRY_QUEUE=m9-telemetry-ingest
      - SQS_PATIENT_EVENTS_QUEUE=patient-events-queue
      - DYNAMODB_TELEMETRY_TABLE=m9-telemetry-readings
      - AWS_REGION=us-east-1
      - AWS_ACCESS_KEY_ID=test
      - AWS_SECRET_ACCESS_KEY=test
      - SIMULATOR_ENABLED=true
    depends_on:
      db:
        condition: service_healthy
      localstack:
        condition: service_healthy

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: m9monitoring
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=sns,sqs,dynamodb
      - DEFAULT_REGION=us-east-1
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:4566/_localstack/health"]
      interval: 5s
      timeout: 3s
      retries: 5
```

Initialize LocalStack resources with a startup script:

```bash
# scripts/init-localstack.sh
#!/bin/bash
awslocal sns create-topic --name monitoring-events
awslocal sqs create-queue --queue-name m9-telemetry-ingest
awslocal sqs create-queue --queue-name patient-events-queue
awslocal sqs create-queue --queue-name m6-monitoring-sub
awslocal sqs create-queue --queue-name m8-monitoring-sub
awslocal sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:000000000000:monitoring-events \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-east-1:000000000000:m6-monitoring-sub
awslocal sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:000000000000:monitoring-events \
    --protocol sqs \
    --notification-endpoint arn:aws:sqs:us-east-1:000000000000:m8-monitoring-sub

# DynamoDB table for telemetry readings
awslocal dynamodb create-table \
    --table-name m9-telemetry-readings \
    --attribute-definitions \
        AttributeName=patient_id,AttributeType=S \
        AttributeName=recorded_at,AttributeType=S \
    --key-schema \
        AttributeName=patient_id,KeyType=HASH \
        AttributeName=recorded_at,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "LocalStack resources initialized."
```

### Spring Boot dependency notes

Add the AWS SDK v2 DynamoDB Enhanced client to `backend/pom.xml`:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb-enhanced</artifactId>
</dependency>
```

Configure the client to honour `AWS_ENDPOINT_URL` so the same code targets LocalStack in dev and real DynamoDB in prod — mirror the pattern already used in `AwsSqsConfig.java`.
