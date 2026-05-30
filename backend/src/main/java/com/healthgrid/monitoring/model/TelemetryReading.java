package com.healthgrid.monitoring.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TelemetryReading — vital signs reading for a patient.
 *
 * <p>Stored in <b>DynamoDB</b> (not Postgres): high write throughput, time-series access.
 * The DynamoDB table key is {@code patientId} (partition) + {@code recordedAt} (sort);
 * the mapping is defined programmatically in {@code config.DynamoDbConfig}, so this stays
 * a plain Lombok bean with domain types ({@link UUID}, {@link LocalDateTime}).
 *
 * <p>The link to the relational {@link Patient} (in Postgres) is the shared
 * {@code patientId} — there is no JPA relationship anymore.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryReading {

    /** Synthetic id kept for API compatibility; NOT the DynamoDB key. */
    private UUID id;

    /** Partition key: the Postgres Patient id this reading belongs to. */
    private UUID patientId;

    /** Heart rate in BPM. Normal range: 60-100. */
    private Float heartRate;

    /** Oxygen saturation in %. Normal range: 95-100. */
    private Float spO2;

    /** Systolic blood pressure in mmHg. Normal range: 90-120. */
    private Float systolicPressure;

    /** Diastolic blood pressure in mmHg. Normal range: 60-80. */
    private Float diastolicPressure;

    /** Body temperature in °C. Normal range: 36.5-37.5. */
    private Float temperature;

    /** Sort key: when the reading was recorded. */
    private LocalDateTime recordedAt;

    /** Epoch-seconds TTL attribute; DynamoDB auto-purges the item after this time. */
    private Long expiresAt;
}
