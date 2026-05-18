package com.healthgrid.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

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
    public SqsClient sqsClient() {
        // TODO(core): reemplazar cliente/config directa por el mecanismo de publicacion/consumo estandar provisto por Core.
        AwsCredentialsIdentity identity = AwsCredentialsIdentity.create(awsAccessKey, awsSecretKey);
        IdentityProvider<AwsCredentialsIdentity> credentialsProvider = new IdentityProvider<>() {
            @Override
            public Class<AwsCredentialsIdentity> identityType() { return AwsCredentialsIdentity.class; }
            @Override
            public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(ResolveIdentityRequest request) {
                return CompletableFuture.completedFuture(identity);
            }
        };

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
