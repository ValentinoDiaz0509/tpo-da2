package com.healthgrid.monitoring.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Patient entity representing a patient in the hospital monitoring system.
 * Each patient can have multiple telemetry readings and alerts.
 */
@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patient_id", columnList = "id"),
    @Index(name = "idx_patient_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"telemetryReadings", "alerts", "createdAt", "updatedAt"})
@ToString(exclude = {"telemetryReadings", "alerts"})
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @NotBlank(message = "Patient name cannot be null or empty")
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank(message = "Room number cannot be null or empty")
    @Column(nullable = false, length = 50)
    private String room;

    @NotBlank(message = "Bed number cannot be null or empty")
    @Column(nullable = false, length = 50)
    private String bed;

    @NotNull(message = "Patient status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * One-to-Many relationship: Una paciente tiene muchas lecturas de telemetría
     */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TelemetryReading> telemetryReadings = new ArrayList<>();

    /**
     * One-to-Many relationship: Un paciente tiene muchas alertas
     */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Alert> alerts = new ArrayList<>();

}

