package com.healthgrid.monitoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.healthgrid.monitoring.model.AlertSeverity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Alert.
 * Represents an alert triggered when a rule condition is met.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Patient Alert DTO")
public class AlertDTO {

    @Schema(description = "Alert unique identifier")
    private UUID id;

    @NotNull(message = "Patient ID cannot be null")
    @Schema(description = "Patient unique identifier")
    private UUID patientId;

    @NotNull(message = "Severity cannot be null")
    @Schema(description = "Alert severity level", example = "CRITICAL")
    private AlertSeverity severity;

    @NotBlank(message = "Alert message cannot be blank")
    @Schema(description = "Alert message describing the condition", example = "Heart rate exceeded 120 BPM for 5 minutes")
    private String message;

    @Schema(description = "Whether the alert has been acknowledged")
    private Boolean acknowledged;

    @Schema(description = "Name of the staff member who acknowledged the alert")
    private String acknowledgedBy;

    @Schema(description = "Timestamp when the alert was acknowledged")
    private LocalDateTime acknowledgedAt;

    @Schema(description = "Timestamp when the alert was triggered")
    private LocalDateTime triggeredAt;

}
