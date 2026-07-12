package com.healthgrid.monitoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.RuleOperator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Rule.
 * Represents a condition that can trigger alerts based on telemetry data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Monitoring Rule DTO")
public class RuleDTO {

    @Schema(description = "Rule unique identifier")
    private UUID id;

    @NotBlank(message = "Metric name cannot be blank")
    @Schema(description = "Name of the metric to monitor", example = "heartRate")
    private String metricName;

    @NotNull(message = "Operator cannot be null")
    @Schema(description = "Comparison operator", example = "GREATER_THAN")
    private RuleOperator operator;

    @NotNull(message = "Threshold cannot be null")
    @Schema(description = "Threshold value", example = "120.0")
    private Float threshold;

    @NotNull(message = "Duration seconds cannot be null")
    @Positive(message = "Duration seconds must be positive")
    @Schema(description = "Duration in seconds for condition to trigger alert", example = "300")
    private Integer durationSeconds;

    @NotNull(message = "Severity cannot be null")
    @Schema(description = "Alert severity when triggered", example = "CRITICAL")
    private AlertSeverity severity;

    @Schema(description = "Description of the rule", example = "Alert if heart rate exceeds 120 BPM for 5 minutes")
    private String description;

    @Schema(description = "Whether this rule is active")
    private Boolean enabled;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

}
