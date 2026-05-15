package com.healthgrid.monitoring.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rule entity representing a monitoring rule that can trigger alerts.
 * Rules define conditions for when alerts should be generated based on telemetry data.
 * 
 * Example:
 * - metricName: "heartRate"
 * - operator: GREATER_THAN
 * - threshold: 120.0
 * - durationSeconds: 300 (must be exceeded for 5 minutes)
 * - severity: CRITICAL
 */
@Entity
@Table(name = "rules", indexes = {
    @Index(name = "idx_rule_metric_enabled", columnList = "metric_name, enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Name of the metric to monitor
     * Examples: "heartRate", "spO2", "systolicPressure", "diastolicPressure", "temperature"
     */
    @NotBlank(message = "Metric name cannot be null or empty")
    @Column(nullable = false, length = 100)
    private String metricName;

    /**
     * Comparison operator for the rule
     */
    @NotNull(message = "Operator cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleOperator operator;

    /**
     * Threshold value that triggers the rule
     */
    @NotNull(message = "Threshold cannot be null")
    @Column(nullable = false)
    private Float threshold;

    /**
     * Duration in seconds for which the condition must be true to trigger the alert
     * 0 = immediate trigger
     * 300 = must be true for 5 minutes before triggering
     */
    @NotNull(message = "Duration seconds cannot be null")
    @Positive(message = "Duration seconds must be positive")
    @Column(nullable = false)
    private Integer durationSeconds;

    /**
     * Severity level of the alert when rule is triggered
     */
    @NotNull(message = "Severity cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    /**
     * Description of what this rule monitors
     */
    @Column(length = 500)
    private String description;

    /**
     * Whether this rule is currently active
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
