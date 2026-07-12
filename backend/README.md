# Backend — Patient Monitoring Service

Spring Boot 3.3 / Java 17 service for real-time hospital patient monitoring.

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

---

## 1. Start the infrastructure (PostgreSQL + LocalStack SQS)

From the `backend/` directory:

```bash
docker-compose up -d
```

This starts two containers:

| Container | Service | Port |
|---|---|---|
| `postgres-monitoring` | PostgreSQL 15 | `5432` |
| `localstack-monitoring` | LocalStack (SQS) | `4566` |

**Database credentials:**

| Field | Value |
|---|---|
| Host | `localhost:5432` |
| Database | `monitoring_db` |
| Username | `postgres` |
| Password | `postgres` |

Wait for both containers to be healthy before starting the app:

```bash
docker-compose ps
```

To stop the infrastructure:

```bash
docker-compose down
```

To stop and delete all data (including the Postgres volume):

```bash
docker-compose down -v
```

---

## 2. Run the Spring Boot application

```bash
mvn spring-boot:run
```

The app starts on port `8080` with context path `/api/v1`.

On first startup, Hibernate (`ddl-auto: update`) creates the schema automatically — no migration step needed.

`DataSeeder` also runs on startup and inserts demo patients, rules, and alerts.

### Useful URLs

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html |
| Health check | http://localhost:8080/api/v1/actuator/health |
| OpenAPI spec | http://localhost:8080/api/v1/openapi.json |

---

## 3. Run the tests

Tests use an embedded H2 database and the Spring Cloud Stream test binder — **no Docker required**.

```bash
mvn test
```

Run a single test class or method:

```bash
mvn test -Dtest=PatientServiceTest
mvn test -Dtest=PatientServiceTest#methodName
```

---

## 4. Build a runnable JAR

```bash
mvn clean package
```

Output: `target/monitoring-service-1.0.0.jar`

Run it directly (infrastructure must be up first):

```bash
java -jar target/monitoring-service-1.0.0.jar
```

---

## 5. Environment variables

All variables have working defaults for local development. Override them in production:

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `healthgrid-monitoring-secret-key-...` | HS512 signing secret |
| `JWT_EXPIRATION` | `86400000` | Token TTL in milliseconds (24 h) |
| `JWT_ISSUER` | `Module10-Core` | JWT issuer claim |
| `MODULE6_WEBHOOK_URL` | `http://localhost:8086/webhooks/...` | Emergency alert webhook target |

---

## 6. Notes

- The **telemetry simulator** is enabled by default and generates fake readings every 3 seconds. Disable it when sending real upstream telemetry by setting `simulator.enabled=false`.
- SQS queues are auto-created in LocalStack on startup (`spring.cloud.stream.aws-sqs.auto-create-queue: true`).
- Sample HTTP requests are in `requests.http` (compatible with IntelliJ HTTP Client and VS Code REST Client).
