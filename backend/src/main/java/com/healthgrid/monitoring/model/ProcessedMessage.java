package com.healthgrid.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @Column(name = "message_fingerprint", nullable = false, updatable = false)
    private String messageFingerprint;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false)
    private String sensorId;

    @Column(nullable = false)
    private UUID patientId;
}
