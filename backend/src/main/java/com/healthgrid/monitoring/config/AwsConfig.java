package com.healthgrid.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import java.net.URI;

/**
 * AWS Configuration for SQS client and event serialization.
 * 
 * Handles:
 * - SqsClient bean creation with proper credentials and region
 * - LocalStack endpoint configuration for development
 * - ObjectMapper configuration for event serialization
 * - Support for AWS standard endpoints in production
 */
@Configuration
public class AwsConfig {

    // TODO(core): mover esta configuracion a la infraestructura/contrato compartido cuando Core defina la integracion real.
    @Value("${aws.credentials.access-key:test}")
    private String awsAccessKey;

    @Value("${aws.credentials.secret-key:test}")
    private String awsSecretKey;

    @Value("${aws.sqs.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.sqs.endpoint:}")
    private String awsEndpoint;

    /**
     * Create and configure the SQS client bean.
     * 
     * For local development with LocalStack, uses custom endpoint.
     * For production, uses AWS-managed endpoint.
     *
     * @return configured SqsClient instance
     */
    @Bean
    @ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public SqsClient sqsClient() {
        // TODO(core): reemplazar cliente/config directa por el mecanismo de publicacion/consumo estandar provisto por Core.
        // Create credentials provider
        AwsBasicCredentials credentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        // Build SqsClient
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider);

        // Use custom endpoint if provided (for LocalStack)
        if (awsEndpoint != null && !awsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        return builder.build();
    }

    /**
     * Create and configure ObjectMapper for JSON serialization/deserialization.
     * 
     * - Handles Java 8 Time API (LocalDateTime, etc.)
     * - Indented output for readability
     * - ISO8601 datetime formatting
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Support Java 8 Time API (LocalDateTime, LocalDate, etc.)
        mapper.registerModule(new JavaTimeModule());
        
        // Use ISO8601 format for dates (not timestamps)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Pretty print for debugging
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        return mapper;
    }

}
