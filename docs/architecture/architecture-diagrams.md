# Architecture Diagrams — Módulo 9: Monitoreo de Pacientes

Visual companion to [`aws-deployment-guide.md`](./aws-deployment-guide.md). Each diagram answers one question; read in order or jump to the one you need.

| # | Diagram | Question it answers |
|---|---|---|
| 1 | [System context](#1-system-context) | Who talks to M9? |
| 2 | [AWS deployment topology](#2-aws-deployment-topology) | Where does each piece run? |
| 3 | [Backend application layers](#3-backend-application-layers) | How is the Spring Boot service structured internally? |
| 4 | [Authentication (JWT) sequence](#4-authentication-jwt-sequence) | How does a nurse get a token? |
| 5 | [Real-time monitoring user journey](#5-real-time-monitoring-user-journey) | What happens when a nurse opens the dashboard? |
| 6 | [Telemetry ingestion & rule evaluation](#6-telemetry-ingestion--rule-evaluation) | How does a sensor reading become an alert? |
| 7 | [Persistence split: Postgres vs DynamoDB](#7-persistence-split-postgres-vs-dynamodb) | What data lives in which store? |
| 8 | [Alert fan-out](#8-alert-fan-out) | Once an alert exists, who hears about it? |

---

## 1. System context

M9 lives in a "modules" landscape (M6 Internación, M10 Core). Its boundaries are: people (medical staff), medical devices (IoT sensors), and the two sibling modules. Everything inside the dashed box is what *we* build and operate.

```mermaid
flowchart LR
    Nurse([Nurse / Medical Staff])
    Doctor([Attending Physician])
    Sensor([IoT Vital-Signs Sensor<br/>Philips · GE])

    subgraph M9["Módulo 9 — Monitoreo de Pacientes (our system)"]
        direction TB
        SPA[React SPA<br/>appvalen]
        API[Spring Boot API<br/>monitoring-service]
        SPA <--> API
    end

    M10[(Módulo 10 — Core<br/>JWT Issuer)]
    M6[(Módulo 6 — Internación<br/>Admission Module)]
    M8[(Módulo 8 — Patient Portal)]

    Nurse -->|HTTPS · dashboard| SPA
    Doctor -->|HTTPS · dashboard| SPA
    Sensor -->|Publish telemetry JSON| API

    API -->|Validate Bearer JWT| M10
    M6 -->|Admission events| API
    API -->|Emergency alert webhook / SNS| M6
    API -->|Patient-state events via SNS| M8

    classDef ext fill:#eef,stroke:#669,stroke-width:1px;
    class M10,M6,M8,Sensor,Nurse,Doctor ext;
```

---

## 2. AWS deployment topology

Where each box from diagram 1 actually runs in AWS. Public-facing components (ALB) sit in public subnets; the JVM, Postgres, DynamoDB, and queues live in private subnets or are accessed via VPC endpoints.

```mermaid
flowchart TB
    Internet((Internet))

    subgraph AWS["AWS Account · us-east-1"]
        Route53[Route 53<br/>DNS]
        ACM[ACM<br/>TLS cert]

        subgraph VPC["VPC"]
            subgraph PubSub["Public subnets · 2 AZs"]
                ALB[Application<br/>Load Balancer<br/>HTTPS · WebSocket]
            end

            subgraph PrivSub["Private subnets · 2 AZs"]
                subgraph ECS["ECS Fargate Cluster · health-grid"]
                    Task1[m9-monitoring task<br/>Spring Boot JVM<br/>port 8080]
                    Task2[m9-monitoring task<br/>Spring Boot JVM<br/>port 8080]
                end
                RDS[(RDS PostgreSQL<br/>db.t3.micro · Multi-AZ)]
            end
        end

        DDB[(DynamoDB<br/>m9-telemetry-readings<br/>PAY_PER_REQUEST + TTL)]
        SNS[/SNS Topic<br/>monitoring-events/]
        SQS_T[/SQS<br/>m9-telemetry-ingest/]
        SQS_P[/SQS<br/>patient-events-queue/]
        SQS_M6[/SQS<br/>m6-monitoring-sub/]
        SQS_M8[/SQS<br/>m8-monitoring-sub/]

        Secrets[Secrets Manager<br/>DB creds · JWT secret]
        CW[CloudWatch Logs<br/>+ Metrics]
        ECR[ECR<br/>health-grid/m9-monitoring]
    end

    Internet --> Route53 --> ALB
    ACM -.-> ALB
    ALB --> Task1
    ALB --> Task2

    Task1 --> RDS
    Task2 --> RDS
    Task1 --> DDB
    Task2 --> DDB
    Task1 -->|consume| SQS_T
    Task2 -->|consume| SQS_T
    Task1 -->|consume| SQS_P
    Task2 -->|consume| SQS_P
    Task1 -->|publish| SNS
    Task2 -->|publish| SNS

    SNS --> SQS_M6
    SNS --> SQS_M8

    Task1 -.read.-> Secrets
    Task2 -.read.-> Secrets
    Task1 -.logs.-> CW
    Task2 -.logs.-> CW
    ECR -.pull.-> ECS

    classDef store fill:#fff8dc,stroke:#a80,stroke-width:1px;
    classDef queue fill:#e0f7ff,stroke:#0a7,stroke-width:1px;
    class RDS,DDB store;
    class SNS,SQS_T,SQS_P,SQS_M6,SQS_M8 queue;
```

---

## 3. Backend application layers

Inside a single Spring Boot task, the code is organised by role. Three *inbound* paths (REST, SQS, scheduled simulator) all converge on the same service layer.

```mermaid
flowchart TB
    subgraph Inbound["Inbound (3 paths into the same domain)"]
        REST[REST Controllers<br/>PatientController<br/>RuleController<br/>AlertController<br/>MonitoringController<br/>TelemetryReadingController]
        WS[WebSocket Endpoint<br/>STOMP · SockJS<br/>/api/v1/ws]
        Cons[SQS Consumers<br/>TelemetryConsumer<br/>PatientEventConsumer]
        Sim[Scheduled Job<br/>TelemetrySimulatorService<br/>simulator.enabled]
        WH[Webhook Receiver<br/>InternacionWebhookController]
    end

    subgraph CrossCut["Cross-cutting"]
        SecF[JwtAuthenticationFilter<br/>+ SecurityConfig]
        WSCfg[WebSocketConfig<br/>broker /topic]
    end

    subgraph Domain["Service / Domain layer"]
        PatientSvc[PatientService]
        TeleSvc[TelemetryReadingService]
        RuleSvc[RuleService]
        AlertSvc[AlertService]
        RuleEng[RuleEngineService<br/>10-min lookback]
        EvtPub[EventPublisherService<br/>SNS publish + Module 6 webhook]
        JwtP[JwtTokenProvider<br/>Core JWKS]
    end

    subgraph Data["Data layer"]
        PatRepo[(PatientRepository)]
        RuleRepo[(RuleRepository)]
        AlertRepo[(AlertRepository)]
        TeleDao[(TelemetryReadingDao<br/>DynamoDB Enhanced)]
    end

    REST --> SecF --> PatientSvc
    SecF --> TeleSvc
    SecF --> RuleSvc
    SecF --> AlertSvc
    WS --> WSCfg
    Cons --> TeleSvc
    Cons --> PatientSvc
    Sim --> TeleSvc
    WH --> PatientSvc

    TeleSvc --> RuleEng
    RuleEng --> AlertSvc
    AlertSvc --> EvtPub
    EvtPub --> WSCfg

    PatientSvc --> PatRepo
    RuleSvc --> RuleRepo
    AlertSvc --> AlertRepo
    TeleSvc --> TeleDao
    RuleEng --> TeleDao

    REST -.token check.-> JwtP
    SecF -.validate.-> JwtP

    classDef inbound fill:#fef3e2,stroke:#d80;
    classDef domain fill:#e8f5e9,stroke:#2a7;
    classDef data fill:#fff8dc,stroke:#a80;
    class REST,WS,Cons,Sim,WH inbound;
    class PatientSvc,TeleSvc,RuleSvc,AlertSvc,RuleEng,EvtPub,JwtP domain;
    class PatRepo,RuleRepo,AlertRepo,TeleDao data;
```

---

## 4. Authentication (JWT) sequence

How a nurse's browser gets a token from Module 10 (Core), then uses it against the monitoring service.

```mermaid
sequenceDiagram
    actor Nurse
    participant SPA as React SPA
    participant Core as Module 10 Core
    participant JwtP as JwtTokenProvider
    participant Filter as JwtAuthenticationFilter
    participant API as Protected REST Endpoint

    Nurse->>SPA: enter credentials
    SPA->>Core: POST /auth/login<br/>{email, password}
    Core-->>SPA: 200 OK · {token, user}
    SPA->>SPA: store token in localStorage

    Nurse->>SPA: open /monitoring
    SPA->>Filter: GET /api/v1/patients/monitoring<br/>Authorization: Bearer <JWT>
    Filter->>JwtP: validate(token via Core JWKS)
    JwtP-->>Filter: claims · OK
    Filter->>API: forward with SecurityContext
    API-->>SPA: 200 OK · patient list
    SPA-->>Nurse: render dashboard

```

---

## 5. Real-time monitoring user journey

Once authenticated, the SPA combines REST snapshots (initial list) with a STOMP WebSocket subscription (live alerts). This is the path a nurse follows from login to acknowledging an alert.

```mermaid
sequenceDiagram
    actor Nurse
    participant SPA as React SPA
    participant API as Spring Boot API
    participant Broker as STOMP Broker<br/>(in-memory)
    participant Rule as RuleEngineService

    Nurse->>SPA: open dashboard
    SPA->>API: GET /api/v1/patients/monitoring
    API-->>SPA: patient list + last vitals
    SPA->>Broker: SockJS · CONNECT /api/v1/ws
    SPA->>Broker: SUBSCRIBE /topic/monitoring/{patientId}
    Note over Broker: per-patient topics

    Nurse->>SPA: click patient row
    SPA->>API: GET /api/v1/patients/{id}
    SPA->>API: GET /api/v1/telemetry/patient/{id}/range
    API-->>SPA: time-series chart data

    par Background telemetry pipeline
        Rule->>Rule: evaluate incoming reading
        Rule->>Broker: SEND /topic/monitoring/{patientId}<br/>{alert}
        Broker-->>SPA: push alert frame
        SPA-->>Nurse: red banner · audible cue
    end

    Nurse->>SPA: acknowledge alert
    SPA->>API: PATCH /api/v1/alerts/{id}/acknowledge
    API-->>SPA: 200 OK
    API->>Broker: SEND /topic/monitoring/{patientId}<br/>{ack}
    Broker-->>SPA: state refresh
```

---

## 6. Telemetry ingestion & rule evaluation

The hot path that runs every few seconds, per patient, per sensor. Devices never talk to the API directly — they publish to SQS, which decouples device cadence from app availability.

```mermaid
sequenceDiagram
    participant Device as IoT Sensor<br/>Philips IntelliVue
    participant SQS as SQS<br/>m9-telemetry-ingest
    participant Consumer as TelemetryConsumer<br/>(Spring Cloud Stream)
    participant TeleSvc as TelemetryReadingService
    participant DDB as DynamoDB<br/>m9-telemetry-readings
    participant Rule as RuleEngineService
    participant AlertSvc as AlertService
    participant PG as Postgres<br/>alerts
    participant Pub as EventPublisherService

    Device->>SQS: publish {sensor_id, patient_id,<br/>metrics, unit_metadata}
    SQS-->>Consumer: deliver message (long-poll)
    Consumer->>TeleSvc: recordReading(reading)
    TeleSvc->>DDB: PutItem<br/>PK=patient_id · SK=recorded_at
    DDB-->>TeleSvc: OK

    Consumer->>Rule: evaluate(reading)
    Rule->>DDB: Query last 10 min<br/>for sustained violations
    DDB-->>Rule: window of readings

    alt threshold violated
        Rule->>AlertSvc: createAlert(severity, msg)
        AlertSvc->>PG: INSERT alert
        AlertSvc->>Pub: publish(alert)
        Pub-->>Pub: WebSocket push (see diagram 8)
        Pub-->>Pub: SNS publish (see diagram 8)
    else within thresholds
        Rule-->>Consumer: no action
    end

    Consumer->>SQS: delete message (ack)

    Note over Device,SQS: Cadence ≈ 1 Hz per patient<br/>per sensor — DynamoDB chosen for<br/>predictable single-key writes.
```

---

## 7. Persistence split: Postgres vs DynamoDB

The polyglot rationale in one picture. Relational entities with rich queries stay in Postgres; high-write time-series go to DynamoDB.

```mermaid
flowchart LR
    subgraph PG["RDS PostgreSQL — relational, low write rate"]
        direction TB
        P[Patient<br/>id · mrn · name · status<br/>roomNumber · diagnosis]
        R[Rule<br/>id · metric · operator<br/>threshold · duration · severity<br/>enabled]
        A[Alert<br/>id · patient_id · severity<br/>message · acknowledged<br/>triggered_at]
        U[User / Audit<br/>id · username · role]
        P ---|1..N| A
        R ---|N..N evaluated against| P
    end

    subgraph DDB["DynamoDB — high-write time-series"]
        direction TB
        T[m9-telemetry-readings<br/>PK patient_id · SK recorded_at<br/>heart_rate · spo2 · pressure<br/>temperature · expires_at TTL]
    end

    A -. references .-> P
    A -. triggered by query over .-> T

    classDef pg fill:#e0eaff,stroke:#558;
    classDef dd fill:#fff0d9,stroke:#a70;
    class P,R,A,U pg;
    class T dd;
```

**Query patterns at a glance**

| Use case | Store | Operation |
|---|---|---|
| List active patients in ICU | Postgres | `SELECT … WHERE status='CRITICAL'` |
| Show patient profile | Postgres | `findById` |
| Last 10 min of vitals for rule eval | DynamoDB | `Query(PK=patient_id, SK>=now-10m, Limit=N)` |
| Telemetry chart on PatientDetail | DynamoDB | `Query(PK=patient_id, SK between t1 and t2)` |
| List unacknowledged alerts | Postgres | `SELECT … WHERE acknowledged=false` |
| Old telemetry cleanup | DynamoDB | TTL on `expires_at` (no app code) |

---

## 8. Alert fan-out

Once `RuleEngineService` produces an `Alert`, it goes three places at once: persisted, pushed to the nursing dashboard in real time, and published to the SNS topic for sibling modules.

```mermaid
flowchart LR
    RE[RuleEngineService] --> AlertSvc[AlertService]
    AlertSvc -->|1. persist| PG[(Postgres · alert)]
    AlertSvc --> Pub[EventPublisherService]

    Pub -->|2a. real-time push| WS[STOMP Broker<br/>/topic/monitoring/&#123;id&#125;]
    WS --> SPA[Nurse SPA<br/>banner + sound]

    Pub -->|2b. publish event| SNS[/SNS<br/>monitoring-events/]
    SNS --> Q1[/SQS m6-monitoring-sub/]
    SNS --> Q2[/SQS m8-monitoring-sub/]
    Q1 --> M6[Módulo 6<br/>Internación]
    Q2 --> M8[Módulo 8<br/>Patient Portal]

    Pub -.optional direct webhook.-> M6

    classDef store fill:#fff8dc,stroke:#a80;
    classDef queue fill:#e0f7ff,stroke:#0a7;
    classDef ext fill:#eef,stroke:#669;
    class PG store;
    class SNS,Q1,Q2,WS queue;
    class M6,M8,SPA ext;
```

**Delivery guarantees per channel**

| Channel | Latency | Retry | If subscriber down |
|---|---|---|---|
| WebSocket push | < 100 ms | None — fire-and-forget | Alert lost for that session (DB still has it; SPA refetches on reconnect) |
| SNS → SQS fan-out | < 1 s | SQS visibility timeout + DLQ | Message held up to 14 days in SQS |
| Direct webhook to M6 | < 500 ms | App retries 3× with backoff | Falls back to SNS path |

---

## Reading order suggestions

- **Onboarding a new dev:** 1 → 3 → 5 → 6
- **Reviewing infra/cost:** 2 → 7 → 8
- **Debugging an alert that didn't fire:** 6 → 8
- **Planning M10 cutover:** 4 (the boxed seam)
