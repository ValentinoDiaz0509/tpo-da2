package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.config.DynamoDbConfig;
import com.healthgrid.monitoring.model.TelemetryReading;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link TelemetryReadingRepository} against a real DynamoDB
 * (DynamoDB Local in a Testcontainers container). Exercises the actual query paths:
 * partition query, sort-key range (the rule-engine lookback), latest-first ordering,
 * client-side threshold filtering, and delete-by-patient.
 */
@Testcontainers
class TelemetryReadingRepositoryIT {

    private static final String TABLE = "telemetry-readings-test";

    @Container
    static final GenericContainer<?> DYNAMO =
        new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
            .withExposedPorts(8000);

    static DynamoDbClient client;
    static TelemetryReadingRepository repository;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000);
        client = DynamoDbClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();

        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(client).build();
        DynamoDbTable<TelemetryReading> table = enhanced.table(TABLE, DynamoDbConfig.TELEMETRY_SCHEMA);

        table.createTable();
        try (DynamoDbWaiter waiter = client.waiter()) {
            waiter.waitUntilTableExists(b -> b.tableName(TABLE));
        }

        repository = new TelemetryReadingRepository(table);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void savesAndQueriesByPatientInDescendingOrder() {
        UUID patientId = UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 5, 29, 10, 0, 0);

        repository.save(reading(patientId, base, 80f, 98f));
        repository.save(reading(patientId, base.plusMinutes(1), 82f, 97f));
        repository.save(reading(patientId, base.plusMinutes(2), 130f, 90f));

        List<TelemetryReading> all = repository.findByPatientId(patientId);

        assertThat(all).hasSize(3);
        // most recent first
        assertThat(all.get(0).getRecordedAt()).isEqualTo(base.plusMinutes(2));
        assertThat(repository.findLatestReadingForPatient(patientId).getHeartRate()).isEqualTo(130f);
    }

    @Test
    void queriesSortKeyRangeForLookbackWindow() {
        UUID patientId = UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 5, 29, 12, 0, 0);
        for (int i = 0; i < 5; i++) {
            repository.save(reading(patientId, base.plusMinutes(i), 70f + i, 99f));
        }

        // window covering minutes 1..3 inclusive -> 3 readings
        List<TelemetryReading> window = repository.findReadingsByPatientAndTimeRange(
            patientId, base.plusMinutes(1), base.plusMinutes(3));

        assertThat(window).hasSize(3);
        assertThat(window).allSatisfy(r ->
            assertThat(r.getRecordedAt()).isBetween(base.plusMinutes(1), base.plusMinutes(3)));
    }

    @Test
    void filtersByThresholdAndDeletesByPatient() {
        UUID patientId = UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 5, 29, 14, 0, 0);
        repository.save(reading(patientId, base, 75f, 98f));
        repository.save(reading(patientId, base.plusMinutes(1), 140f, 91f)); // high HR
        repository.save(reading(patientId, base.plusMinutes(2), 90f, 88f));  // low SpO2

        assertThat(repository.findByPatientAndHighHeartRate(patientId, 120f)).hasSize(1);
        assertThat(repository.findByPatientAndLowSpO2(patientId, 92f)).hasSize(1);

        long deleted = repository.deleteByPatientId(patientId);
        assertThat(deleted).isEqualTo(3);
        assertThat(repository.findByPatientId(patientId)).isEmpty();
    }

    private static TelemetryReading reading(UUID patientId, LocalDateTime at, float hr, float spo2) {
        return TelemetryReading.builder()
            .id(UUID.randomUUID())
            .patientId(patientId)
            .heartRate(hr)
            .spO2(spo2)
            .systolicPressure(120f)
            .diastolicPressure(80f)
            .temperature(37f)
            .recordedAt(at)
            .expiresAt(at.toEpochSecond(java.time.ZoneOffset.UTC) + 3600)
            .build();
    }
}
