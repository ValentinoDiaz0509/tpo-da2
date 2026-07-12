package com.healthgrid.monitoring.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TelemetryReading entity representing vital signs and metrics readings for a patient.
 * Each reading contains multiple vital sign metrics with a timestamp.
 */
@Entity
@Table(name = "telemetry_readings", indexes = {
    @Index(name = "idx_telemetry_patient_id", columnList = "patient_id"),
    @Index(name = "idx_telemetry_recorded_at", columnList = "recorded_at"),
    @Index(name = "idx_telemetry_patient_recorded", columnList = "patient_id, recorded_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"patient", "recordedAt"})
@ToString(exclude = {"patient"})
public class TelemetryReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Many-to-One relationship: Muchas lecturas pertenecen a un paciente
     */
    @NotNull(message = "Patient cannot be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_telemetry_patient"))
    private Patient patient;

    /**
     * Heart rate in beats per minute (BPM)
     * Normal range: 60-100 BPM
     */
    @NotNull(message = "Heart rate cannot be null")
    @Column(nullable = false)
    private Float heartRate;

    /**
     * Oxygen saturation in percentage (%)
     * Normal range: 95-100%
     */
    @NotNull(message = "SpO2 cannot be null")
    @Column(nullable = false)
    private Float spO2;

    /**
     * Systolic blood pressure in mmHg
     * Normal range: 90-120 mmHg
     */
    @NotNull(message = "Systolic pressure cannot be null")
    @Column(nullable = false)
    private Float systolicPressure;

    /**
     * Diastolic blood pressure in mmHg
     * Normal range: 60-80 mmHg
     */
    @NotNull(message = "Diastolic pressure cannot be null")
    @Column(nullable = false)
    private Float diastolicPressure;

    /**
     * Body temperature in Celsius (°C)
     * Normal range: 36.5-37.5°C
     */
    @NotNull(message = "Temperature cannot be null")
    @Column(nullable = false)
    private Float temperature;

    /**
     * Timestamp when the reading was recorded
     */
    @CreationTimestamp
    @NotNull(message = "Recorded timestamp cannot be null")
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

}
