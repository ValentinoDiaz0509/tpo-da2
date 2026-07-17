# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Two-app monorepo for a hospital patient monitoring system:

- `backend/` — Spring Boot 3.3 / Java 17 service (`com.healthgrid.monitoring`), Maven build, artifact `monitoring-service`.
- `frontend/` — React 19 + Vite 8 SPA (`appvalen`), JS only (no TypeScript).
- `docs/` — Project documentation, organised into subfolders:
  - `architecture/` — system design, AWS deployment guide, Mermaid diagram set, telemetry flow. Start here for a new dev.
  - `guides/` — how-to guides: `DEVELOPMENT_GUIDE.md`, `STARTUP_GUIDE.txt`, `QUICKSTART.md`, `TELEMETRY_INGESTION_GUIDE.md`, `FRONTEND_COMMUNICATION_LAYER.md`.
  - `project/` — TPO spec, sprint plan, team, AWS user accounts.
  - `reports/` — historical phase-completion summaries (mostly read-only context).
  - `examples/` — `TELEMETRY_MESSAGE_EXAMPLES.json` (sample SQS payloads).

There is no root build script. Each app builds independently from its own directory.

## Common Commands

### Backend (run from `backend/`)

```bash
docker-compose up -d                  # PostgreSQL (5432) + RabbitMQ (5672, UI 15672) + WireMock Core (8081)
mvn spring-boot:run                   # Run app (port 8080, context path /api/v1)
mvn clean package                     # Build jar at target/monitoring-service-1.0.0.jar
mvn test                              # All tests (H2, messaging disabled — no docker needed)
mvn test -Dtest=PatientServiceTest    # Single test class
mvn test -Dtest=PatientServiceTest#methodName   # Single test method
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`. Health: `/api/v1/actuator/health`. Sample HTTP requests live in `backend/requests.http`.

### Frontend (run from `frontend/`)

```bash
npm install
npm run dev        # Vite dev server (default port 5173)
npm run build      # Production build
npm run lint       # ESLint (flat config in eslint.config.js)
npm run preview    # Preview built bundle
```

The frontend assumes the backend at `http://localhost:8080/api/v1` (hardcoded in `src/services/api.js` and `src/services/websocket.js`). CORS in `SecurityConfig.java` whitelists `5173`, `3000`, and `8080`.

## High-Level Architecture

### Backend layered structure
Single Spring Boot module organized by package role under `com.healthgrid.monitoring`:
`controller/` → `service/` → `repository/` → `model/` (JPA entities) and `dto/` (API/event payloads, including `dto/auth/` for JWT). Cross-cutting code lives in `config/`, `security/`, and `consumer/`. Constructor injection via Lombok `@RequiredArgsConstructor`; entities and DTOs use Lombok `@Data`/`@Builder`. Hibernate is in `ddl-auto: update` — schema evolves from JPA annotations; there is no Flyway.

### Three input paths feed the same domain

1. **REST controllers** (`controller/`) — CRUD for patients, rules, alerts, telemetry, plus the dashboard aggregator `MonitoringController` at `GET /patients/monitoring` and the webhook receiver `InternacionWebhookController`.
2. **RabbitMQ listener** (`consumer/AdmissionEventListener`) — `@RabbitListener` on the `monitoring.requests` queue of the M10 Core event bus (`spring.rabbitmq.*`, default host `queue.healthgrid.cantero.ar`; queue name via `healthgrid.rabbit.monitoring-queue`). Messages arrive wrapped in a `CoreEventEnvelope` whose `payload` field is a JSON *string* — double parse required. Gated by `healthgrid.rabbit.enabled`. Queues/events/bindings are provisioned from the Core API, never declared locally (see `docs/architecture/shared_docs/comunicacion-tutorial.md`).
3. **Telemetry simulator** (`service/TelemetrySimulatorService`) — `@Scheduled` bean that fabricates telemetry every `simulator.rate` ms (default 3s) and calls `TelemetryConsumer.processTelemetryMessage` directly. Telemetry is internal-only — it does **not** flow through the Core bus; only resulting alerts are published outward. Gated by `simulator.enabled` (default true). **Disable this when running scenarios with real upstream telemetry**, otherwise simulated readings will contend with real ones.

### Rule engine and alert fan-out
`RuleEngineService` evaluates each ingested `TelemetryReading` against active `Rule`s using a 10-minute lookback window for sustained violations, generating `Alert`s. Alerts are then:
- Persisted via `AlertRepository`.
- Pushed to subscribed clients via STOMP over WebSocket (`SimpMessagingTemplate` → `/topic/monitoring/{patientId}`).
- Published to the M10 Core event bus (`CoreEventPublisher` → `POST /events/log`, event ids from `healthgrid.module10.core.events.*`, defaults 16/17) **and**, during the transition, to the legacy Module 6 webhook (`EventPublisherService`, URL from `healthgrid.module6.webhook.url`).

The Spanish word for "in-progress" used in this codebase is *internación*; the patient lifecycle uses `PatientStatus` (`ADMITTED`/`STABLE`/`CRITICAL`/`INACTIVE`/...) and `AlertSeverity` (`CRITICAL`/`WARNING`/...).

### Real-time delivery
`WebSocketConfig` registers a SockJS-fallback STOMP endpoint at `/ws` (full path `/api/v1/ws`) with in-memory broker on `/topic`. Frontend subscribes per-patient at `/topic/monitoring/{patientId}` (see `frontend/src/services/websocket.js`).

### Security model
Stateless JWT via `security/SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`. `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/**` are public; everything else requires a Bearer token. Tokens are issued by Module 10 (Core); this service validates them through Core's JWKS endpoint (`${MODULE10_CORE_URL}/.well-known/jwks.json`). `AuthenticationController` only exposes `GET /auth/me` for the current authenticated principal.

### External integration boundary
This service refers to itself as a module that talks to numbered peers (Module 6, Module 10). `MODULE6_WEBHOOK_URL` env var redirects emergency-alert webhooks. The webhook *receiver* lives at `InternacionWebhookController` for admission events from the patient-admission module.

### Frontend architecture
`src/App.jsx` wires React Router with an `AuthProvider` (`context/AuthContext.jsx`) and a `ProtectedRoute` wrapper. Two views: `Monitoring` (list of all patients) and `PatientDetail` (per-patient charts + WebSocket subscription). All HTTP goes through `services/api.js`, which reads the JWT from `localStorage` and force-redirects to `/login` on 401. Recharts renders telemetry; `lucide-react` provides icons.

## Conventions and Gotchas

- **Backend context path is `/api/v1`** — every URL the frontend or `requests.http` calls must include it. The WebSocket endpoint is `/api/v1/ws`.
- **Source files mix English and Spanish.** Code identifiers are English; log messages, comments, and DTO descriptions are often Spanish (especially `internacion`/`monitoreo`/`paciente`). Match the convention of the file you are editing.
- **Tests** run on H2 via `backend/src/test/resources/application.yml` with all external messaging disabled (`healthgrid.rabbit.enabled=false`, Core/webhook/simulator off) — they do **not** need PostgreSQL or RabbitMQ. `BaseIntegrationTest` is the shared test base.
- **All wall-clock reads use UTC** (`LocalDateTime.now(ZoneOffset.UTC)`) — telemetry `recordedAt`, alert `triggeredAt`/`acknowledgedAt`, and outbound M6 payloads (serialized with a `'Z'` suffix). Never use bare `LocalDateTime.now()`; it breaks lookback windows and the cross-module timestamp contract.
- **Lombok is required at compile time** (annotation processor is configured in `pom.xml`). If your IDE shows "cannot find symbol" on `log`/getters/setters, enable Lombok plugin/annotation processing.
- **`ddl-auto: update`** means destructive schema changes from JPA edits can silently fail or leave orphan columns; review entity changes carefully. There is no migration tool.
- **`DataSeeder`** (`config/DataSeeder.java`) populates demo data on startup — be aware when troubleshooting unexpected rows.
- **The frontend has no test setup.** Only ESLint is configured; do not claim test coverage for frontend changes.
- The Spring Boot Maven plugin excludes Lombok from the fat jar; running `java -jar` is fine without extra flags.
