package com.healthgrid.monitoring.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Alert entity representing an alert triggered when a rule condition is met.
 * Alerts notify medical staff about abnormal patient conditions that require attention.
 */
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_patient_id", columnList = "patient_id"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_triggered_at", columnList = "triggered_at"),
    @Index(name = "idx_alert_acknowledged", columnList = "acknowledged"),
    @Index(name = "idx_alert_patient_severity", columnList = "patient_id, severity")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"patient", "triggeredAt", "createdAt", "updatedAt"})
@ToString(exclude = {"patient"})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Many-to-One relationship: Muchas alertas pertenecen a un paciente
     */
    @NotNull(message = "Patient cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_alert_patient"))
    private Patient patient;

    /**
     * Severity level of the alert
     */
    @NotNull(message = "Severity cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    /**
     * Alert message describing the condition
     * Example: "Heart rate exceeded 120 BPM for 5 minutes"
     */
    @NotBlank(message = "Alert message cannot be null or empty")
    @Column(nullable = false, length = 1000)
    private String message;

    /**
     * Whether this alert has been acknowledged by medical staff
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    /**
     * Name of the medical staff member who acknowledged the alert
     */
    @Column(length = 255)
    private String acknowledgedBy;

    /**
     * Timestamp when the alert was acknowledged
     */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /**
     * Timestamp when the alert was triggered/generated
     */
    @CreationTimestamp
    @NotNull(message = "Triggered timestamp cannot be null")
    @Column(name = "triggered_at", nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
