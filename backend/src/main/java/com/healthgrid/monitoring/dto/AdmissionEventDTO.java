package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdmissionEventDTO {

    @JsonProperty("patient_id")
    private UUID patientId;

    @JsonProperty("alert_severity")
    private String alertSeverity;

    @JsonProperty("location")
    private String location;

    @JsonProperty("triggered_rule")
    private String triggeredRule;

    @JsonProperty("metric_name")
    private String metricName;

    @JsonProperty("metric_value")
    private Double metricValue;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sensor_id")
    private String sensorId;

    @JsonProperty("acknowledgment_required")
    private Boolean acknowledgmentRequired;

    @JsonProperty("priority_level")
    private String priorityLevel;
}
