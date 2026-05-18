# Telemetry Ingestion System Documentation

## Overview

The Telemetry Ingestion System is a real-time health monitoring solution that processes vital signs data from IoT sensors via AWS SQS. It automatically evaluates patient metrics against predefined health rules and generates alerts when thresholds are exceeded.

## Architecture

```
IoT Sensors (Hospitals/Clinics)
           │
           ├─→ Philips Monitor
           ├─→ GE Healthcare Scanner
           └─→ Other Medical Devices
                    │
                    ├─ JSON Telemetry Messages
                    │
                    ▼
        AWS SQS (telemetry-readings-queue)
                    │
        Spring Cloud Stream binder
                    │
                    ▼
        TelemetryConsumer Bean
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
TelemetryReadingService  HealthRuleEvaluationService
        │                       │
        ├─ Save to DB           ├─ Evaluate Rules
        │                       ├─ Generate Alerts
        ▼                       ▼
    PostgreSQL          Alert Storage & Notification
```

## Components

### 1. **TelemetryMessageDTO**
Represents the message structure received from AWS SQS.

**Structure:**
```json
{
  "sensor_id": "SENSOR-ICU-001",
  "patient_id": "550e8400-e29b-41d4-a716-446655440000",
  "metrics": {
    "heart_rate": 85.5,
    "spo2": 98.5,
    "systolic_pressure": 120.0,
    "diastolic_pressure": 80.0,
    "temperature": 37.2
  },
  "unit_metadata": {
    "unit_id": "UNIT-001",
    "unit_location": "ICU-Room-101",
    "device_model": "Philips IntelliVue",
    "device_serial": "SN-789456",
    "firmware_version": "2.1.5"
  }
}
```

**Related DTOs:**
- `TelemetryMetricsDTO`: Vital signs container with all metric values
- `UnitMetadataDTO`: Device and location information

### 2. **TelemetryConsumer**
Spring Cloud Stream consumer bean that:
- Listens to AWS SQS queue `telemetry-readings-queue`
- Receives messages in `TelemetryMessageDTO` format
- Converts to `TelemetryReadingDTO`
- Persists via `TelemetryReadingService`
- Evaluates rules via `HealthRuleEvaluationService`
- Handles errors and logging

**Configuration:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        telemetryEventInput:
          destination: telemetry-readings-queue
          group: telemetry-service-group
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
            back-off-max-interval: 10000
```

### 3. **TelemetryReadingService**
Business logic for telemetry operations:

**Methods:**
- `recordReading(UUID patientId, TelemetryReadingDTO)` - Save reading to DB
- `getLatestReading(UUID patientId)` - Get most recent reading
- `getPatientReadings(UUID patientId)` - Get all readings
- `getReadingsByTimeRange(UUID, start, end)` - Filter by time
- `getHighHeartRateReadings(UUID, threshold)` - Anomaly detection
- `getLowSpO2Readings(UUID, threshold)` - Anomaly detection

### 4. **HealthRuleEvaluationService**
Evaluates health rules against telemetry readings:

**Methods:**
- `evaluateReadingAgainstRules(TelemetryReading)` - Main evaluation logic
- Supports operators: `>`, `>=`, `<`, `<=`, `==`, `!=`
- Generates alerts when thresholds are exceeded
- Supports metrics: `heart_rate`, `spo2`, `systolic_pressure`, `diastolic_pressure`, `temperature`

**Rule Evaluation Flow:**
1. Retrieve all active (enabled) rules
2. For each rule:
   - Extract metric value from reading
   - Compare against threshold using operator
   - Generate alert if condition met
3. Return list of generated alerts

### 5. **TelemetryReadingController**
REST API endpoints for direct telemetry submission and retrieval:

**Endpoints:**
```
POST   /api/v1/telemetry/patient/{patientId}
GET    /api/v1/telemetry/patient/{patientId}/latest
GET    /api/v1/telemetry/patient/{patientId}
GET    /api/v1/telemetry/patient/{patientId}/range
GET    /api/v1/telemetry/patient/{patientId}/high-heart-rate
GET    /api/v1/telemetry/patient/{patientId}/low-spo2
```

## Processing Pipeline

### Step 1: Message Reception
```
IoT Sensor publishes JSON → AWS SQS queue (telemetry-readings-queue)
```

### Step 2: Message Consumption
```
TelemetryConsumer.telemetryEventInput() bean listens for messages
Automatic deserialization to TelemetryMessageDTO
```

### Step 3: Data Persistence
```
TelemetryConsumer converts TelemetryMessageDTO to TelemetryReadingDTO
TelemetryReadingService.recordReading() persists to PostgreSQL
```

### Step 4: Rule Evaluation
```
Retrieve saved TelemetryReading entity
HealthRuleEvaluationService.evaluateReadingAgainstRules()
Iterates through all active rules
For each rule: extract metric → compare → generate alert if needed
```

### Step 5: Alert Generation
```
If any rule condition is met:
- Create Alert entity with:
  - Patient reference
  - Severity level (from rule)
  - Alert message with metric value
  - Timestamp
- Save to database
- Log warning message
```

### Step 6: Error Handling
```
If processing fails:
- Log error with full context (sensor ID, patient ID, exception)
- Future: Send to dead-letter queue
- Future: Trigger error alert notifications
```

## Example End-to-End Workflow

### Scenario: Patient with High Heart Rate Rule

**Setup:**
1. Patient created: UUID `550e8400-e29b-41d4-a716-446655440000`
2. Rule created: "Alert if HR > 120 BPM for more than 5 minutes"
   ```json
   {
     "metricName": "heart_rate",
     "operator": "GREATER_THAN",
     "threshold": 120.0,
     "durationSeconds": 300,
     "severity": "CRITICAL",
     "enabled": true
   }
   ```

**Execution:**
1. IoT sensor sends telemetry with HR=135 BPM via SQS
2. TelemetryConsumer receives message
3. Reading saved: `TelemetryReading { patient_id, heart_rate: 135.0, ... }`
4. Rule evaluated:
   - Extract HR: 135.0
   - Check: 135.0 > 120.0 → TRUE
   - Condition met: Create alert
5. Alert created:
   ```
   {
     "patient_id": "550e8400-e29b-41d4-a716-446655440000",
     "severity": "CRITICAL",
     "message": "Alert: heart_rate value (135.00) triggered rule violation...",
     "acknowledged": false
   }
   ```
6. Alert saved and logged
7. Medical staff queries unacknowledged alerts and sees this alert
8. Staff reviews patient and acknowledges: `PATCH /alerts/{id}/acknowledge`

## Configuration

### application.yml
```yaml
spring:
  cloud:
    stream:
      bindings:
        telemetryEventInput:
          destination: telemetry-readings-queue
          group: telemetry-service-group
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
            back-off-max-interval: 10000
      aws-sqs:
        auto-create-queue: true

aws:
  sqs:
    endpoint: http://localhost:4566  # LocalStack for local development
    region: us-east-1
```

## Database Schema

### TelemetryReading Table
```sql
CREATE TABLE telemetry_reading (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
  heart_rate FLOAT,
  spo2 FLOAT,
  systolic_pressure FLOAT,
  diastolic_pressure FLOAT,
  temperature FLOAT,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_telemetry_patient_id ON telemetry_reading(patient_id);
CREATE INDEX idx_telemetry_recorded_at ON telemetry_reading(recorded_at);
CREATE INDEX idx_telemetry_patient_recorded ON telemetry_reading(patient_id, recorded_at);
```

### Rule Table
```sql
CREATE TABLE rule (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  metric_name VARCHAR(100) NOT NULL,
  operator VARCHAR(50) NOT NULL,
  threshold FLOAT NOT NULL,
  duration_seconds INT,
  severity VARCHAR(50),
  description TEXT,
  enabled BOOLEAN DEFAULT true,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Alert Table
```sql
CREATE TABLE alert (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
  severity VARCHAR(50) NOT NULL,
  message TEXT,
  acknowledged BOOLEAN DEFAULT false,
  acknowledged_by VARCHAR(255),
  acknowledged_at TIMESTAMP,
  triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_patient_id ON alert(patient_id);
CREATE INDEX idx_alert_acknowledged ON alert(acknowledged);
```

## Supported Metrics

| Metric | Normal Range | Unit | Operations |
|--------|--------------|------|------------|
| heart_rate | 60-100 | BPM | >, >=, <, <=, ==, != |
| spo2 | 95-100 | % | >, >=, <, <=, ==, != |
| systolic_pressure | 90-120 | mmHg | >, >=, <, <=, ==, != |
| diastolic_pressure | 60-80 | mmHg | >, >=, <, <=, ==, != |
| temperature | 36.5-37.5 | °C | >, >=, <, <=, ==, != |

## Logging

All telemetry processing is logged at appropriate levels:

- **DEBUG**: Metric values, rule evaluations without alerts
- **INFO**: Reading recorded, processing completed
- **WARN**: Alerts generated, rule violations
- **ERROR**: Processing failures, missing data

Example logs:
```
INFO  - Received telemetry message from sensor: SENSOR-ICU-001 for patient: 550e8400-e29b-41d4-a716-446655440000
INFO  - Telemetry reading saved with ID: 6f3a1234-5678-90ab-cdef-1234567890ab
WARN  - Generated 1 alert(s) for patient: 550e8400-e29b-41d4-a716-446655440000
WARN  - ALERT [alert-uuid] - Patient: patient-uuid, Severity: CRITICAL, Message: ...
```

## Future Enhancements

1. **Notification Service Integration**
   - Alert medical staff via email/SMS/push notifications
   - Integration with hospital communication systems

2. **Dead-Letter Queue (DLQ)**
   - Route failed messages to DLQ for retry
   - Error tracking and alerting

3. **Advanced Rule Engine**
   - Time-series anomaly detection
   - ML-based threshold learning
   - Composite rules (AND/OR logic)

4. **Performance Optimization**
   - Batch rule evaluation
   - In-memory caching of active rules
   - Async alert generation

5. **Audit Trail**
   - Track all telemetry ingestion
   - Rule change history
   - Alert acknowledgment trail

6. **Integration Tests**
   - LocalStack SQS testing
   - End-to-end workflow tests
   - Load testing for high-frequency sensors

## Testing

### Manual Testing with LocalStack
```bash
# Start LocalStack
docker-compose up -d

# Send test message to SQS queue
aws sqs send-message \
  --queue-url http://localhost:4566/000000000000/telemetry-readings-queue \
  --message-body '{"sensor_id":"SENSOR-001","patient_id":"550e8400-e29b-41d4-a716-446655440000",...}' \
  --endpoint-url http://localhost:4566
```

### API Testing
See `TELEMETRY_API_EXAMPLES.sh` for complete curl examples.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Consumer not receiving messages | Check SQS queue configuration, verify credentials |
| Messages processed but no alerts | Check rule enabled status, verify threshold values |
| Database constraint errors | Ensure patient exists before creating readings/alerts |
| JSON parsing errors | Validate message structure against schema |
