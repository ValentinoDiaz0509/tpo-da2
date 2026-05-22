# Flujo de Ingesta de Telemetría - Diagrama de Arquitectura

## 1. FLUJO GENERAL DE DATOS

```
┌─────────────────────────────────────────────────────────────────┐
│                    HOSPITALES/CLÍNICAS                          │
│                  (IoT Medical Devices)                          │
└─────────────┬──────────────────────────────────────────────────┘
              │
              │ JSON Telemetry Messages
              │ (TelemetryMessageDTO)
              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   AWS SQS Service                               │
│          (telemetry-readings-queue)                             │
│  [Queue: IoT → Hospital Monitoring Service]                     │
└─────────────┬──────────────────────────────────────────────────┘
              │
              │ Spring Cloud Stream Consumer
              ▼
┌─────────────────────────────────────────────────────────────────┐
│            PATIENT MONITORING SERVICE                           │
│          (Spring Boot 3.3 Application)                          │
│                                                                 │
│  ┌─────────────────────────────────────────┐                  │
│  │  TelemetryConsumer.telemetryEventInput  │                  │
│  │  - Deserialize message                  │                  │
│  │  - Convert to DTO                       │                  │
│  │  - Call services                        │                  │
│  └──────────────┬──────────────┬──────────┘                  │
│                 │              │                             │
│        ┌────────▼──────┐   ┌───▼──────────────┐             │
│        │ Telemetry     │   │ Health Rule      │             │
│        │ Reading       │   │ Evaluation       │             │
│        │ Service       │   │ Service          │             │
│        │               │   │                  │             │
│        │ - recordRead  │   │ - evaluate       │             │
│        │ - getLatest   │   │   against rules  │             │
│        │ - getByRange  │   │ - generate alerts│             │
│        └────────┬──────┘   └───┬──────────────┘             │
│                 │              │                             │
│                 │ Save          │ Evaluate                    │
│                 ▼              ▼                             │
│        ┌─────────────────────────────┐                      │
│        │    PostgreSQL Database       │                      │
│        │                              │                      │
│        │ Tables:                      │                      │
│        │ - telemetry_reading          │                      │
│        │ - rule                       │                      │
│        │ - alert                      │                      │
│        │ - patient                    │                      │
│        └─────────────────────────────┘                      │
│                                                              │
│  ┌─────────────────────────────────────────┐               │
│  │  REST Controllers                       │               │
│  │  ├─ TelemetryReadingController         │               │
│  │  ├─ RuleController                     │               │
│  │  ├─ AlertController                    │               │
│  │  └─ PatientController                  │               │
│  └─────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
              │
              │ REST APIs
              ▼
┌─────────────────────────────────────────────────────────────────┐
│              MEDICAL STAFF DASHBOARDS                            │
│          (Hospital Frontend Applications)                       │
│  - View real-time patient vitals                                │
│  - See generated alerts                                        │
│  - Acknowledge alerts                                          │
│  - Review historical data                                      │
└─────────────────────────────────────────────────────────────────┘
```

## 2. MENSAJE DE TELEMETRÍA - FLUJO DETALLADO

```
IoT Sensor publishes:
┌─────────────────────────────────────────┐
│  {                                      │
│    "sensor_id": "SENSOR-ICU-001",      │
│    "patient_id": "uuid...",             │
│    "metrics": {                         │
│      "heart_rate": 85.5,               │
│      "spo2": 98.5,                     │
│      ...                               │
│    },                                   │
│    "unit_metadata": { ... }            │
│  }                                     │
└─────────────┬──────────────────────────┘
              │
              │ Send to SQS
              ▼
    AWS SQS Queue (telemetry-readings-queue)
              │
              │ Spring Cloud Stream Binding
              │ spring:
              │   cloud:
              │     stream:
              │       bindings:
              │         telemetryEventInput:
              │           destination: telemetry-readings-queue
              │           group: telemetry-service-group
              │
              ▼
┌──────────────────────────────────────────────────────┐
│  @Bean                                               │
│  public Consumer<TelemetryMessageDTO>               │
│         telemetryEventInput()                       │
│  {                                                   │
│    return telemetryMessage -> {                     │
│      // 1. Deserialize                             │
│      // 2. Convert                                 │
│      // 3. Save                                    │
│      // 4. Evaluate                                │
│      // 5. Generate Alerts                         │
│    };                                               │
│  }                                                   │
└──────────────────────────────────────────────────────┘
```

## 3. PROCESAMIENTO EN CONSUMER - PASO A PASO

```
① RECEIVE MESSAGE
   Input: TelemetryMessageDTO {
     sensor_id: "SENSOR-ICU-001"
     patient_id: "550e8400-e29b-41d4-a716-446655440000"
     metrics: { hr: 85.5, spo2: 98.5, ... }
     unitMetadata: { ... }
   }
   ↓
② VALIDATE
   ✓ PatientId not null
   ✓ Metrics complete
   ✓ Valid format
   ↓
③ CONVERT DTO
   TelemetryMessageDTO → TelemetryReadingDTO
   {
     patientId: "550e8400-e29b-41d4-a716-446655440000"
     heartRate: 85.5
     spO2: 98.5
     systolicPressure: 120.0
     diastolicPressure: 80.0
     temperature: 37.2
   }
   ↓
④ PERSIST READING
   telemetryReadingService.recordReading(patientId, readingDTO)
   ├─ Find Patient by ID
   ├─ Create TelemetryReading entity
   ├─ Save to DB
   └─ Return saved DTO with ID
   ↓
⑤ GET SAVED ENTITY
   Retrieve TelemetryReading from DB for evaluation
   ↓
⑥ EVALUATE RULES
   healthRuleEvaluationService.evaluateReadingAgainstRules(reading)
   ├─ Get all active (enabled) rules
   ├─ For each rule:
   │  ├─ Extract metric value from reading
   │  ├─ Compare: value OPERATOR threshold
   │  └─ If TRUE: Generate Alert
   └─ Return list of alerts
   ↓
⑦ HANDLE ALERTS
   If alerts generated:
   ├─ Log warning message
   ├─ Save Alert entity
   └─ (Future) Trigger notifications
   ↓
⑧ COMPLETE
   Message processed successfully
   Ready for next message
```

## 4. EVALUACIÓN DE REGLAS - DETALLE

```
Rule Configuration:
┌──────────────────────────────────────┐
│ Metric: "heart_rate"                │
│ Operator: "GREATER_THAN"            │
│ Threshold: 120.0                    │
│ Severity: "CRITICAL"                │
│ Enabled: true                       │
└──────────────────────────────────────┘

Incoming Reading:
┌──────────────────────────────────────┐
│ Heart Rate: 135.0 BPM               │
│ SpO2: 98.5%                         │
│ ...                                 │
└──────────────────────────────────────┘

Evaluation Process:
   1. Extract metric: heartRate = 135.0
   2. Get operator: GREATER_THAN (>)
   3. Compare: 135.0 > 120.0
   4. Result: TRUE
      ↓
   5. Create Alert:
      - Patient: UUID
      - Severity: CRITICAL (from rule)
      - Message: "Alert: heart_rate value (135.00) triggered..."
      - AcknowledgedAt: null (new alert)
      - TriggeredAt: NOW()
      ↓
   6. Save Alert to DB
      ↓
   7. Log: "ALERT [uuid] - Patient: uuid, Severity: CRITICAL..."
```

## 5. OPERADORES SOPORTADOS

```
┌───────────────────────────────────────────────────────┐
│ Operator          │ Symbol │ Example                │
├───────────────────┼─────────┼──────────────────────┤
│ GREATER_THAN      │ >       │ HR > 120              │
│ GREATER_OR_EQUAL  │ >=      │ SPO2 >= 94            │
│ LESS_THAN         │ <       │ HR < 50               │
│ LESS_OR_EQUAL     │ <=      │ Temp <= 36            │
│ EQUAL             │ ==      │ Status == CRITICAL    │
│ NOT_EQUAL         │ !=      │ Status != NORMAL      │
└───────────────────────────────────────────────────────┘

Comparación de Flotantes:
  EQUAL: abs(value - threshold) < 0.01
  NOT_EQUAL: abs(value - threshold) >= 0.01
```

## 6. CICLO DE VIDA DE UNA ALERTA

```
① ALERT CREATED
   Generated by rule evaluation
   ├─ Patient: UUID
   ├─ Severity: CRITICAL/WARNING/INFO
   ├─ Message: Description
   ├─ Acknowledged: false
   ├─ TriggeredAt: NOW()
   └─ Status: NEW (unacknowledged)

② IN QUEUE
   Alert persisted to DB
   ├─ Staff queries: GET /alerts/unacknowledged
   ├─ Dashboard displays unacknowledged alerts
   └─ High priority alerts highlighted by severity

③ REVIEWED
   Medical staff views alert
   ├─ Checks: Patient ID, Severity, Message
   ├─ Reviews: Latest vital signs
   ├─ Takes action (medication, intervention, etc.)
   └─ Ready to acknowledge

④ ACKNOWLEDGED
   Medical staff marks as reviewed
   ├─ PATCH /alerts/{id}/acknowledge?acknowledgedBy=Dr.%20Smith
   ├─ Update: acknowledged = true
   ├─ Update: acknowledgedBy = "Dr. Smith"
   ├─ Update: acknowledgedAt = NOW()
   └─ Status: CLOSED

⑤ ARCHIVED
   Alert remains in DB for audit trail
   ├─ Accessible via: GET /alerts/patient/{patientId}
   ├─ Visible in historical reports
   └─ Used for trend analysis
```

## 7. FLUJO COMPLETO - CASO DE USO

```
SCENARIO: Patient with HR > 120 Rule triggers alert

TIME 0s:
  ┌─ IoT Monitor records HR = 135 BPM
  └─ Publishes to AWS SQS

TIME 1s:
  ┌─ Message arrives in queue
  └─ TelemetryConsumer receives it

TIME 2s:
  ┌─ Message deserialized to TelemetryMessageDTO
  ├─ Converted to TelemetryReadingDTO
  └─ Saved to DB as TelemetryReading (ID: reading-uuid-123)

TIME 3s:
  ┌─ HealthRuleEvaluationService retrieves reading
  ├─ Gets all active rules
  ├─ Finds rule: HR > 120, Severity=CRITICAL
  ├─ Evaluates: 135 > 120? YES
  └─ Generates Alert (ID: alert-uuid-456)

TIME 4s:
  ┌─ Alert saved to DB
  ├─ Log: "ALERT [alert-uuid-456] - Patient: patient-uuid, Severity: CRITICAL"
  └─ Medical staff notified (future: email/SMS)

TIME 10s:
  ┌─ Dashboard queries: GET /alerts/unacknowledged/critical
  ├─ Response: [{ id: "alert-uuid-456", severity: "CRITICAL", ... }]
  └─ Alert displays in red on dashboard

TIME 45s:
  ┌─ Dr. Smith reviews patient vital signs
  ├─ Takes appropriate action
  ├─ Calls: PATCH /alerts/alert-uuid-456/acknowledge?acknowledgedBy=Dr.%20Smith
  ├─ Alert updated: acknowledged=true, acknowledgedBy="Dr. Smith", acknowledgedAt=NOW()
  └─ Alert removed from unacknowledged list

TIME 60s+:
  ┌─ Alert remains in database for audit
  ├─ Visible in patient alert history
  ├─ Used for reporting: "3 critical alerts in last hour"
  └─ Helps identify patient patterns
```

## 8. MÉTRICAS SOPORTADAS & RANGOS NORMALES

```
┌────────────────────┬──────────────┬──────┬────────────────────┐
│ Métrica            │ Rango Normal │ Unit │ Observaciones      │
├────────────────────┼──────────────┼──────┼────────────────────┤
│ Heart Rate         │ 60-100       │ BPM  │ < 50 = Bradycardia │
│                    │              │      │ > 120 = Tachycardia│
│                    │              │      │                    │
│ Oxygen  Sat (SpO2) │ 95-100       │ %    │ < 94 = Hypoxia     │
│                    │              │      │ Critical < 88%     │
│                    │              │      │                    │
│ Systolic Pressure  │ 90-120       │ mmHg │ < 90 = Hypotension │
│                    │              │      │ > 140 = Hypertens. │
│                    │              │      │                    │
│ Diastolic Pressure │ 60-80        │ mmHg │ Used with systolic │
│                    │              │      │ for BP classification
│                    │              │      │                    │
│ Temperature        │ 36.5-37.5    │ °C   │ < 35 = Hypothermia │
│                    │              │      │ > 38.5 = Fever     │
│                    │              │      │ > 39.5 = High fever│
└────────────────────┴──────────────┴──────┴────────────────────┘
```

## 9. MANEJO DE ERRORES

```
ERROR SCENARIOS:

① Invalid Message Format
   → Log ERROR
   → Skip processing
   → (Future) Send to DLQ

② Patient Not Found
   → Log ERROR: "Patient not found: {id}"
   → Skip persisting reading
   → (Future) Alert ops team

③ Database Connection Error
   → Log ERROR: "DB connection failed"
   → (Future) Retry with backoff
   → (Future) Send to DLQ

④ Rule Evaluation Exception
   → Log ERROR: "Rule evaluation failed for rule: {id}"
   → Continue with next rule
   → Alert logged for debugging

⑤ Alert Generation Exception
   → Log ERROR: "Failed to generate alert"
   → Continue processing
   → Alert missing from DB (to investigate)

ERROR HANDLING IN CONSUMER:
try {
  // Process telemetry
} catch (Exception e) {
  log.error("Error processing telemetry", e);
  // Future: send to DLQ
  // Future: send error alert
}
```

## 10. MONITOREO Y OBSERVABILIDAD

```
LOGS EXPECTED:

INFO Level:
  "Received telemetry message from sensor: SENSOR-ICU-001..."
  "Telemetry reading saved with ID: ..."
  "Fetching telemetry for patient: ..."

WARN Level:
  "Generated 1 alert(s) for patient: ..."
  "ALERT [uuid] - Patient: {}, Severity: {}, Message: {}"

ERROR Level:
  "Error processing telemetry message..."
  "Patient not found with ID: ..."
  "Database error during save..."

DEBUG Level:
  "Metric metric_name not found in reading..."
  "No alerts generated for reading..."

METRICS TO MONITOR:
  ✓ Messages processed per minute
  ✓ Alerts generated per hour
  ✓ Processing latency (P50, P95, P99)
  ✓ Error rate per 1000 messages
  ✓ Database connection pool status
  ✓ SQS queue depth
```

## 11. CHECKLIST DE IMPLEMENTACIÓN

```
✓ TelemetryMessageDTO - JSON deserialization
✓ TelemetryMetricsDTO - Vital signs container
✓ UnitMetadataDTO - Device metadata
✓ TelemetryConsumer - SQS message listener
✓ HealthRuleEvaluationService - Rule evaluation engine
✓ TelemetryReadingService - Persistence layer
✓ TelemetryReadingController - REST API
✓ RuleController - Rule management API
✓ AlertController - Alert management API
✓ Database schema - Tables & indexes
✓ Spring Cloud Stream configuration
✓ AWS SQS binding configuration
✓ Error handling & logging
✓ Documentation & examples
```
