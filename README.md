# HealthGrid – Patient Monitoring

Hospital patient monitoring system: a Spring Boot service that ingests vital-sign telemetry, evaluates configurable rules, raises alerts, and pushes real-time updates to a React dashboard.

This repository is part of a larger multi-module platform — it talks to a Core auth module ("Module 10") for JWT tokens and to an emergency-alert module ("Module 6") via webhooks.

## Stack

| Layer    | Tech                                                                        |
| -------- | --------------------------------------------------------------------------- |
| Backend  | Java 17 · Spring Boot 3.3 · Spring Data JPA · Spring Cloud Stream · Spring Security (JWT) · STOMP/WebSocket |
| Frontend | React 19 · Vite 8 · React Router 7 · Recharts · STOMP.js / SockJS           |
| Data     | PostgreSQL 15 (relational) · DynamoDB (telemetry time-series) · AWS SQS / SNS (LocalStack in dev) |
| Tooling  | Maven · Docker Compose · ESLint · Swagger / OpenAPI                         |

## Repository layout

```
backend/               Spring Boot service (com.healthgrid.monitoring)
frontend/              React + Vite SPA
docs/
├── architecture/      System design, AWS deployment guide, diagram set
├── guides/            How-to guides (dev setup, JWT, telemetry, frontend)
├── project/           TPO spec, sprint plan, team, AWS accounts
├── reports/           Phase-completion summaries (historical)
└── examples/          Sample SQS / telemetry JSON payloads
CLAUDE.md              Guidance for Claude Code agents working in this repo
```

## Quick start

### Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

### 1. Start the infrastructure

```bash
cd backend
docker-compose up -d        # PostgreSQL on :5432, LocalStack SQS on :4566
```

### 2. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API serves at **http://localhost:8080/api/v1** with Swagger UI at `/api/v1/swagger-ui.html` and health at `/api/v1/actuator/health`. A `DataSeeder` populates demo patients on first start.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

Log in through the UI, or generate a token directly:

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"module":"DASHBOARD","userId":"demo"}'
```

## How it works

```
┌────────────────────┐     SQS / REST / Simulator      ┌──────────────────┐
│ Telemetry sources  │ ──────────────────────────────▶ │ Monitoring API   │
└────────────────────┘                                 │  • Rule engine   │
                                                       │  • Alerts        │
┌────────────────────┐     POST /webhooks/...          │  • JWT auth      │
│ Admission module   │ ──────────────────────────────▶ │                  │
└────────────────────┘                                 └────────┬─────────┘
                                                                │
                          ┌─────────────────────────────────────┼─────────────┐
                          ▼                                     ▼             ▼
                     PostgreSQL                       STOMP /topic/...   Module 6 webhook
                                                              │
                                                              ▼
                                                       React dashboard
```

Three input paths feed the same domain: REST controllers, SQS consumers (Spring Cloud Stream), and a built-in `TelemetrySimulatorService` that generates mock readings every 3 s for local development. Disable it with `simulator.enabled=false` when ingesting real telemetry.

## Configuration

Defaults live in `backend/src/main/resources/application.yml`. Common overrides via environment variables:

| Variable              | Purpose                                            | Default                                   |
| --------------------- | -------------------------------------------------- | ----------------------------------------- |
| `JWT_SECRET`          | HS512 signing key (≥256 bits in production)        | dev-only key (do not use in prod)         |
| `JWT_EXPIRATION`      | Token TTL in ms                                    | `86400000` (24h)                          |
| `JWT_ISSUER`          | `iss` claim                                        | `Module10-Core`                           |
| `MODULE6_WEBHOOK_URL` | Where to forward emergency alerts                  | `http://localhost:8086/webhooks/...`      |

## Testing

```bash
cd backend
mvn test                                      # all tests (H2 + test SQS binder, no docker)
mvn test -Dtest=PatientServiceTest            # single class
mvn test -Dtest=PatientServiceTest#methodName # single method
```

The frontend currently ships only ESLint (`npm run lint`); no test runner is configured.

## Further reading

- [`CLAUDE.md`](./CLAUDE.md) — architecture and conventions reference for this repo
- [`docs/architecture/architecture-diagrams.md`](./docs/architecture/architecture-diagrams.md) — 8 Mermaid diagrams covering context, deployment, data flows, and alert fan-out
- [`docs/architecture/aws-deployment-guide.md`](./docs/architecture/aws-deployment-guide.md) — step-by-step ECS Fargate + RDS + DynamoDB deployment
- [`docs/architecture/ARCHITECTURE.md`](./docs/architecture/ARCHITECTURE.md) — layered design overview
- [`docs/guides/DEVELOPMENT_GUIDE.md`](./docs/guides/DEVELOPMENT_GUIDE.md) — how to add endpoints, consumers, and tests
- [`docs/guides/SECURITY_JWT_GUIDE.md`](./docs/guides/SECURITY_JWT_GUIDE.md) — JWT flow and Module 10 integration
- [`docs/guides/TELEMETRY_INGESTION_GUIDE.md`](./docs/guides/TELEMETRY_INGESTION_GUIDE.md) — telemetry pipeline deep-dive
- [`backend/requests.http`](./backend/requests.http) — ready-to-run API examples
