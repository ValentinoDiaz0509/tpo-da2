package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.config.DynamoDbConfig;
import com.healthgrid.monitoring.model.TelemetryReading;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Data access for {@link TelemetryReading} backed by <b>DynamoDB</b>.
 *
 * <p>Replaces the former JPA repository. Reads are single-partition queries keyed by
 * {@code patientId}; the rule-engine lookback window is a sort-key range query. Threshold
 * finders query the partition then filter client-side (no GSI needed for this volume).
 */
@Repository
@RequiredArgsConstructor
public class TelemetryReadingRepository {

    private final DynamoDbTable<TelemetryReading> table;

    public TelemetryReading save(TelemetryReading reading) {
        table.putItem(reading);
        return reading;
    }

    /** All readings for a patient, most recent first. */
    public List<TelemetryReading> findByPatientId(UUID patientId) {
        return queryDescending(patientId);
    }

    /** All readings for a patient, most recent first (alias kept for callers). */
    public List<TelemetryReading> findLatestReadingsByPatient(UUID patientId) {
        return queryDescending(patientId);
    }

    /** Readings within [startTime, endTime] for a patient, most recent first. */
    public List<TelemetryReading> findReadingsByPatientAndTimeRange(
            UUID patientId, LocalDateTime startTime, LocalDateTime endTime) {
        QueryConditional between = QueryConditional.sortBetween(
            keyOf(patientId, startTime),
            keyOf(patientId, endTime));
        return table.query(QueryEnhancedRequest.builder()
                .queryConditional(between)
                .scanIndexForward(false)
                .build())
            .items().stream().collect(Collectors.toList());
    }

    /** Most recent reading for a patient, or {@code null} if none. */
    public TelemetryReading findLatestReadingForPatient(UUID patientId) {
        return table.query(QueryEnhancedRequest.builder()
                .queryConditional(partition(patientId))
                .scanIndexForward(false)
                .limit(1)
                .build())
            .items().stream().findFirst().orElse(null);
    }

    public List<TelemetryReading> findByPatientAndHighHeartRate(UUID patientId, Float threshold) {
        return queryDescending(patientId).stream()
            .filter(r -> r.getHeartRate() != null && r.getHeartRate() > threshold)
            .collect(Collectors.toList());
    }

    public List<TelemetryReading> findByPatientAndLowSpO2(UUID patientId, Float threshold) {
        return queryDescending(patientId).stream()
            .filter(r -> r.getSpO2() != null && r.getSpO2() < threshold)
            .collect(Collectors.toList());
    }

    /** Delete all readings for a patient (used when a patient is removed). */
    public long deleteByPatientId(UUID patientId) {
        List<TelemetryReading> items = queryDescending(patientId);
        items.forEach(r -> table.deleteItem(keyOf(patientId, r.getRecordedAt())));
        return items.size();
    }

    private List<TelemetryReading> queryDescending(UUID patientId) {
        return table.query(QueryEnhancedRequest.builder()
                .queryConditional(partition(patientId))
                .scanIndexForward(false)
                .build())
            .items().stream().collect(Collectors.toList());
    }

    private static QueryConditional partition(UUID patientId) {
        return QueryConditional.keyEqualTo(Key.builder().partitionValue(patientId.toString()).build());
    }

    private static Key keyOf(UUID patientId, LocalDateTime recordedAt) {
        return Key.builder()
            .partitionValue(patientId.toString())
            .sortValue(DynamoDbConfig.TIMESTAMP_FORMAT.format(recordedAt))
            .build();
    }
}
