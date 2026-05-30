package com.healthgrid.monitoring.config;

import com.healthgrid.monitoring.model.TelemetryReading;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * AWS DynamoDB configuration for the telemetry time-series store.
 *
 * <p>Mirrors the {@code AwsConfig}/{@code AwsSqsConfig} pattern: a custom endpoint
 * (LocalStack/DynamoDB-Local) is used in dev, the AWS-managed endpoint in prod.
 * Set {@code aws.dynamodb.endpoint} blank (prod) to target real DynamoDB.
 *
 * <p>The {@link TelemetryReading} mapping is defined here as a {@link StaticTableSchema}
 * so the model can stay a plain Lombok bean. Domain types are stored as DynamoDB strings:
 * UUIDs as their text form, timestamps as a fixed-width ISO pattern so the sort key orders
 * chronologically (and range queries work).
 */
@Configuration
public class DynamoDbConfig {

    /** Fixed-width so lexicographic order == chronological order on the sort key. */
    public static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    @Value("${aws.dynamodb.endpoint:http://localhost:4566}")
    private String endpoint;

    @Value("${aws.dynamodb.region:us-east-1}")
    private String region;

    @Value("${aws.credentials.access-key:test}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:test}")
    private String secretKey;

    @Value("${aws.dynamodb.table-name:m9-telemetry-readings}")
    private String tableName;

    /** Programmatic schema — keeps TelemetryReading free of DynamoDB annotations. */
    public static final TableSchema<TelemetryReading> TELEMETRY_SCHEMA =
        StaticTableSchema.builder(TelemetryReading.class)
            .newItemSupplier(TelemetryReading::new)
            .addAttribute(String.class, a -> a.name("patient_id")
                .getter(t -> t.getPatientId() == null ? null : t.getPatientId().toString())
                .setter((t, s) -> t.setPatientId(s == null ? null : UUID.fromString(s)))
                .tags(StaticAttributeTags.primaryPartitionKey()))
            .addAttribute(String.class, a -> a.name("recorded_at")
                .getter(t -> t.getRecordedAt() == null ? null : TIMESTAMP_FORMAT.format(t.getRecordedAt()))
                .setter((t, s) -> t.setRecordedAt(s == null ? null : LocalDateTime.parse(s, TIMESTAMP_FORMAT)))
                .tags(StaticAttributeTags.primarySortKey()))
            .addAttribute(String.class, a -> a.name("id")
                .getter(t -> t.getId() == null ? null : t.getId().toString())
                .setter((t, s) -> t.setId(s == null ? null : UUID.fromString(s))))
            .addAttribute(Float.class, a -> a.name("heart_rate")
                .getter(TelemetryReading::getHeartRate).setter(TelemetryReading::setHeartRate))
            .addAttribute(Float.class, a -> a.name("spo2")
                .getter(TelemetryReading::getSpO2).setter(TelemetryReading::setSpO2))
            .addAttribute(Float.class, a -> a.name("systolic")
                .getter(TelemetryReading::getSystolicPressure).setter(TelemetryReading::setSystolicPressure))
            .addAttribute(Float.class, a -> a.name("diastolic")
                .getter(TelemetryReading::getDiastolicPressure).setter(TelemetryReading::setDiastolicPressure))
            .addAttribute(Float.class, a -> a.name("temperature")
                .getter(TelemetryReading::getTemperature).setter(TelemetryReading::setTemperature))
            .addAttribute(Long.class, a -> a.name("expires_at")
                .getter(TelemetryReading::getExpiresAt).setter(TelemetryReading::setExpiresAt))
            .build();

    @Bean
    public DynamoDbClient dynamoDbClient() {
        StaticCredentialsProvider credentials =
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials);

        // Blank endpoint -> use the real AWS DynamoDB endpoint (prod).
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public DynamoDbTable<TelemetryReading> telemetryTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TELEMETRY_SCHEMA);
    }
}
