# Frontend Communication Layer - Summary

**Status**: ✅ **BUILD SUCCESS** (All components compiled successfully)

## Overview

Successfully implemented a complete frontend communication layer with:
1. **WebSocket with STOMP** for real-time monitoring updates
2. **RuleEngineService integration** for pushing telemetry data to connected clients
3. **MonitoringController REST API** for dashboard data retrieval

---

## 1. WebSocket Configuration (WebSocketConfig.java)

**Location**: `src/main/java/com/healthgrid/monitoring/config/WebSocketConfig.java`

**Components**:
- STOMP endpoint: `/ws` 
- Message broker: `/topic` destinations
- Application prefix: `/app`
- SockJS fallback for browsers without WebSocket support
- CORS enabled (configure appropriately for production)

**Features**:
```
Client Connection Flow:
┌─────────────────────────────────────┐
│ Client (React Dashboard)            │
│ ws://localhost:8080/api/v1/ws       │
└────────────┬────────────────────────┘
             │ STOMP/WebSocket
             ▼
┌─────────────────────────────────────┐
│ WebSocketConfig (STOMP Broker)      │
│ - Listens on /ws endpoint           │
│ - Manages /topic subscriptions      │
└────────────┬────────────────────────┘
             │
             ▼
/topic/monitoring/{patientId}
             │
             ▼
┌─────────────────────────────────────┐
│ RuleEngineService                   │
│ - Sends MonitoringUpdateDTO         │
│ - Every telemetry processing        │
└─────────────────────────────────────┘
```

---

## 2. Real-Time Update DTOs

### MonitoringUpdateDTO
**Location**: `src/main/java/com/healthgrid/monitoring/dto/MonitoringUpdateDTO.java`

**Purpose**: Real-time telemetry updates sent via WebSocket

**Fields**:
- `patient_id` (UUID) - Which patient's data
- `heart_rate` (Float) - Current heart rate
- `spo2` (Float) - Oxygen saturation
- `systolic_pressure` (Float) - Systolic BP
- `diastolic_pressure` (Float) - Diastolic BP
- `temperature` (Float) - Body temperature
- `timestamp` (LocalDateTime) - When reading was taken

**Sent To**: `/topic/monitoring/{patientId}`
**Frequency**: With each processed telemetry reading
**Format**: JSON (snake_case naming via @JsonProperty)

---

## 3. Dashboard Data DTO

### PatientMonitoringDTO
**Location**: `src/main/java/com/healthgrid/monitoring/dto/PatientMonitoringDTO.java`

**Purpose**: Complete patient monitoring snapshot for dashboard display

**Top-Level Fields**:
- `patient_id` (UUID)
- `patient_name` (String)
- `room` (String)
- `bed` (String)
- `status` (String) - ACTIVE, CRITICAL, WARNING, DISCHARGED
- `latest_metrics` (LatestMetricsDTO) - All vital signs
- `active_alerts` (List<AlertSummaryDTO>) - Unacknowledged alerts
- `last_update` (LocalDateTime)

**Nested DTOs**:

#### LatestMetricsDTO
Contains individual metrics for each vital sign:
- `heart_rate` (MetricDTO)
- `spo2` (MetricDTO)
- `systolic_pressure` (MetricDTO)
- `diastolic_pressure` (MetricDTO)
- `temperature` (MetricDTO)

#### MetricDTO
Individual metric with status:
- `value` (Float) - Current reading
- `unit` (String) - Measurement unit
- `timestamp` (LocalDateTime) - When taken
- `status` (String) - NORMAL, WARNING, CRITICAL
- `rule_threshold` (Float) - Alert threshold

#### AlertSummaryDTO
Summary of active alerts:
- `alert_id` (UUID)
- `severity` (String) - CRITICAL, WARNING, INFO
- `message` (String) - Alert description
- `triggered_at` (LocalDateTime)
- `metric_name` (String) - heart_rate, spo2, etc.
- `metric_value` (Float)

---

## 4. RuleEngineService WebSocket Integration

**Updated**: `src/main/java/com/healthgrid/monitoring/service/RuleEngineService.java`

**Changes**:
- Added `SimpMessagingTemplate` injection
- Added `sendMonitoringUpdate(TelemetryReading)` method
- Called `sendMonitoringUpdate()` after processing each telemetry reading

**Flow**:
```
TelemetryConsumer
    ↓
TelemetryReading received/saved
    ↓
RuleEngineService.evaluateReadingAndGenerateAlerts()
    ├─ Evaluate against active rules
    ├─ Generate alerts if sustained violation
    ├─ Publish admission events for critical alerts
    │
    └─ sendMonitoringUpdate(reading)
        └─ simpMessagingTemplate.convertAndSend()
            └─ /topic/monitoring/{patientId}
                └─ Connected clients receive update
```

**WebSocket Send Method**:
```java
private void sendMonitoringUpdate(TelemetryReading reading) {
    MonitoringUpdateDTO update = MonitoringUpdateDTO.builder()
            .patientId(patient.getId())
            .heartRate(reading.getHeartRate())
            .spO2(reading.getSpO2())
            .systolicPressure(reading.getSystolicPressure())
            .diastolicPressure(reading.getDiastolicPressure())
            .temperature(reading.getTemperature())
            .timestamp(reading.getRecordedAt())
            .build();

    simpMessagingTemplate.convertAndSend(
        "/topic/monitoring/" + patient.getId(),
        update
    );
}
```

**Error Handling**: Non-critical failures in WebSocket sending don't interrupt main processing

---

## 5. Monitoring Controller

**Location**: `src/main/java/com/healthgrid/monitoring/controller/MonitoringController.java`

### Endpoint: GET /api/v1/patients/monitoring

**Purpose**: Retrieve all patients with current monitoring data for dashboard

**Response**:
```json
[
  {
    "patient_id": "550e8400-e29b-41d4-a716-446655440000",
    "patient_name": "Juan Pérez",
    "room": "203",
    "bed": "B",
    "status": "CRITICAL",
    "latest_metrics": {
      "heart_rate": {
        "value": 125.0,
        "unit": "bpm",
        "timestamp": "2026-03-21T12:40:00",
        "status": "CRITICAL",
        "rule_threshold": 100.0
      },
      "spo2": {
        "value": 96.5,
        "unit": "%",
        "timestamp": "2026-03-21T12:40:00",
        "status": "NORMAL",
        "rule_threshold": 100.0
      },
      ...
    },
    "active_alerts": [
      {
        "alert_id": "660e8400-e29b-41d4-a716-446655440001",
        "severity": "CRITICAL",
        "message": "Heart rate exceeded threshold...",
        "triggered_at": "2026-03-21T12:39:00",
        "metric_name": "heart_rate",
        "metric_value": 125.0
      }
    ],
    "last_update": "2026-03-21T12:40:00"
  }
]
```

**Status Determination**:
1. If has CRITICAL alerts → Status = "CRITICAL"
2. Else if has WARNING alerts → Status = "WARNING"
3. Else → Use patient's own status

**Metric Status Logic**:
- NORMAL: Value within expected range (60-100 bpm for heart_rate, etc.)
- WARNING: Value close to limits (within 10 units)
- CRITICAL: Value significantly outside range

---

## 6. Frontend Integration Guide

### React Client Setup (WebSocket)

```javascript
import { Client } from '@stomp/stompjs';

// Connect to WebSocket
const client = new Client({
  brokerURL: 'ws://localhost:8080/api/v1/ws',
  onConnect: () => {
    // Subscribe to patient-specific updates
    client.subscribe(
      '/topic/monitoring/550e8400-e29b-41d4-a716-446655440000',
      (message) => {
        const update = JSON.parse(message.body);
        // Update dashboard with real-time metrics
        updateMetricsDisplay(update);
      }
    );
  },
});

client.activate();
```

### Dashboard Data Retrieval

```javascript
// Fetch all patients with monitoring data
const response = await fetch('/api/v1/patients/monitoring');
const patients = await response.json();

// Display in dashboard
patients.forEach(patient => {
  renderPatientCard(patient);
  subscribeToPatientUpdates(patient.patient_id);
});
```

---

## 7. Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  React Dashboard                         │
├─────────────────────────────────────────────────────────┤
│  Displays:                                               │
│  - All patients with vital signs                        │
│  - Real-time metric updates                             │
│  - Active alerts                                         │
│  - Clinical status                                       │
└────────────────┬──────────────────────────┬─────────────┘
                 │                          │
     ┌───────────▼──────────┐   ┌──────────▼───────────┐
     │ WebSocket Listener    │   │ HTTP REST API        │
     │ /ws endpoint          │   │ GET /monitoring      │
     │ STOMP Protocol        │   │ Periodic polling     │
     └───────────┬──────────┘   └──────────┬───────────┘
                 │                         │
     ┌───────────▼─────────────────────────▼──────────┐
     │           Spring Backend (8080)                │
     ├───────────────────────────────────────────────┤
     │  ┌─────────────────────────────────────────┐  │
     │  │ WebSocketConfig                         │  │
     │  │ - STOMP Broker                          │  │
     │  │ - /topic/monitoring/{patientId}         │  │
     │  │ - SockJS Fallback                       │  │
     │  └─────────────────────────────────────────┘  │
     │                                                │
     │  ┌─────────────────────────────────────────┐  │
     │  │ MonitoringController                    │  │
     │  │ GET /patients/monitoring                │  │
     │  │ Returns: PatientMonitoringDTO[]         │  │
     │  └─────────────────────────────────────────┘  │
     │                                                │
     │  ┌─────────────────────────────────────────┐  │
     │  │ RuleEngineService                       │  │
     │  │ - Processes telemetry readings          │  │
     │  │ - Evaluates against rules               │  │
     │  │ - Sends WebSocket updates               │  │
     │  │ - Publishes to /topic/monitoring/{pid}  │  │
     │  └─────────────────────────────────────────┘  │
     │                                                │
     │  ┌─────────────────────────────────────────┐  │
     │  │ TelemetryConsumer                       │  │
     │  │ - SQS message processor                 │  │
     │  │ - Saves TelemetryReading                │  │
     │  │ - Triggers RuleEngineService            │  │
     │  └─────────────────────────────────────────┘  │
     │                                                │
     │  ┌─────────────────────────────────────────┐  │
     │  │ Database                                │  │
     │  │ - Patients                              │  │
     │  │ - TelemetryReadings                     │  │
     │  │ - Rules                                 │  │
     │  │ - Alerts                                │  │
     │  └─────────────────────────────────────────┘  │
     └───────────────────────────────────────────────┘
                 │
     ┌───────────▼──────────┐
     │ AWS SQS              │
     │ - Telemetry Events   │
     │ - Patient Events     │
     └──────────────────────┘
```

---

## 8. Real-Time Data Flow Example

**Scenario**: Heart rate reading exceeds threshold

```
1. Device sends telemetry to AWS SQS
   └─ heartRate: 125 bpm (normal range: 60-100)

2. TelemetryConsumer receives from SQS
   └─ Creates TelemetryReading entity
   └─ Saves to database
   └─ Calls RuleEngineService

3. RuleEngineService evaluates
   ├─ Fetches active rules for patient
   ├─ Checks if reading violates rules
   ├─ Analyzes historical readings (10-minute lookback)
   ├─ Detects sustained violation (>= 60 seconds)
   └─ Generates CRITICAL alert + publishes to /topic/monitoring/{patientId}

4. WebSocket Server (Spring)
   └─ Broadcasts MonitoringUpdateDTO to all connected clients
      for this patient

5. React Dashboard
   ├─ Receives real-time update
   ├─ Updates metric display (125 bpm in red)
   ├─ Shows alert banner
   └─ Plays alert sound/notification

6. Dashboard Periodic Poll (every 5-10 seconds)
   └─ GET /patients/monitoring
   └─ Refreshes complete patient list with latest data
```

---

## 9. Deployment Considerations

### Development
- WebSocket runs on same server: `ws://localhost:8080/api/v1/ws`
- CORS allows all origins (`*`)
- SockJS fallback enabled for environments without WebSocket

### Production
- Update CORS origins to specific domains:
  ```java
  registry.addEndpoint("/ws")
          .setAllowedOrigins("https://yourdomain.com")
          .withSockJS();
  ```
- Use secure WebSocket (WSS):
  ```javascript
  brokerURL: 'wss://api.yourdomain.com/api/v1/ws'
  ```
- Configure load balancer to support WebSocket:
  - Enable sticky sessions
  - Upgrade Connection header
  - Persistent connections
- Consider Redis for message broker (vs simple in-memory):
  ```java
  config.enableStompBrokerRelay("/topic")
        .setRelayHost("redis.host")
        .setRelayPort(61613);
  ```

---

## 10. Testing Checklist

- [ ] WebSocket connection established from browser
- [ ] MonitoringUpdateDTO received in real-time
- [ ] GET /patients/monitoring returns all patients
- [ ] Patient status correctly computed from alerts
- [ ] Metric status logic works (NORMAL, WARNING, CRITICAL)
- [ ] Real-time updates reflect alert generation
- [ ] Dashboard displays patient list correctly
- [ ] Metric values match database readings
- [ ] Alert messages properly extracted and displayed
- [ ] Multiple concurrent WebSocket clients supported
- [ ] Network disconnect handled gracefully
- [ ] High-frequency telemetry (1 reading/sec) performs well

---

## 11. Files Created/Modified

**New Files** (4):
- `src/main/java/com/healthgrid/monitoring/config/WebSocketConfig.java`
- `src/main/java/com/healthgrid/monitoring/dto/MonitoringUpdateDTO.java`
- `src/main/java/com/healthgrid/monitoring/dto/PatientMonitoringDTO.java`
- `src/main/java/com/healthgrid/monitoring/controller/MonitoringController.java`

**Modified Files** (2):
- `src/main/java/com/healthgrid/monitoring/service/RuleEngineService.java`
- `pom.xml` (added spring-boot-starter-websocket dependency)

---

## 12. Compilation Status

✅ **All components successfully compiled**

```
Total files: 30+
New classes: 4
Modified classes: 1
Dependencies added: 1 (spring-boot-starter-websocket)
Build time: ~2.5 seconds
Exit code: 0 (SUCCESS)
```

---

## Next Steps

1. **Frontend Implementation**
   - Create React components for dashboard
   - Implement WebSocket client
   - Add real-time metric displays
   - Configure alert notifications

2. **Integration Testing**
   - Test WebSocket with LocalStack
   - Verify real-time updates
   - Load test with concurrent patients
   - Test network failure scenarios

3. **Production Deployment**
   - Configure CORS for production domain
   - Set up WSS (secure WebSocket)
   - Configure message broker for clustering
   - Set up monitoring and alerting

4. **Advanced Features**
   - Export patient data
   - Historical chart views
   - Alert acknowledgment tracking
   - Patient search and filtering
