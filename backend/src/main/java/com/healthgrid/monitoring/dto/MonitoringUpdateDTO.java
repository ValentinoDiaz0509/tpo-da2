package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for real-time monitoring updates sent via WebSocket.
 * 
 * Sent to clients subscribed to /topic/monitoring/{patientId}
 * whenever a new telemetry reading is processed.
 * 
 * Contains complete vital signs for dashboard real-time display.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringUpdateDTO {

    @JsonProperty("patient_id")
    private UUID patientId;

    @JsonProperty("heart_rate")
    private Float heartRate;

    @JsonProperty("spo2")
    private Float spO2;

    @JsonProperty("systolic_pressure")
    private Float systolicPressure;

    @JsonProperty("diastolic_pressure")
    private Float diastolicPressure;

    @JsonProperty("temperature")
    private Float temperature;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

}

