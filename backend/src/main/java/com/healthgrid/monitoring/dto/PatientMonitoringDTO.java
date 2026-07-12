package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for patient monitoring dashboard display.
 * 
 * Contains:
 * - Patient basic information
 * - Latest telemetry readings for each metric
 * - Current patient status
 * - Active alerts
 * 
 * Used by: MonitoringController GET /patients/monitoring
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PatientMonitoringDTO {
    
    @JsonProperty("patient_id")
    private UUID patientId;
    
    @JsonProperty("patient_name")
    private String patientName;
    
    @JsonProperty("room")
    private String room;
    
    @JsonProperty("bed")
    private String bed;
    
    @JsonProperty("status") // NORMAL, WARNING, CRITICAL
    private String status;
    
    @JsonProperty("latest_metrics")
    private LatestMetricsDTO latestMetrics;
    
    @JsonProperty("active_alerts")
    private List<AlertSummaryDTO> activeAlerts;
    
    @JsonProperty("last_update")
    private LocalDateTime lastUpdate;
}
