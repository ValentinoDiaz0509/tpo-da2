package com.healthgrid.monitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AWS SQS integration.
 * Configures Spring Cloud Stream for message streaming using AWS SQS.
 * Supports both real AWS SQS and LocalStack for development/testing.
 * 
 * Spring Cloud Stream with SQS Binder is automatically configured.
 * Configuration is provided via application properties (spring.cloud.stream.bindings.*).
 */
@Configuration
@Slf4j
public class AwsSqsConfig {

    @Value("${aws.sqs.endpoint:http://localhost:4566}")
    private String endpoint;

    @Value("${aws.sqs.region:us-east-1}")
    private String region;

    @Value("${aws.credentials.access-key:test}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:test}")
    private String secretKey;

    @Value("${aws.sqs.enabled:true}")
    private boolean enabled;

    /**
     * Initialize AWS SQS configuration.
     * 
     * Configuration Details:
     * - For LocalStack: Uses endpoint http://localhost:4566 with dummy credentials.
     * - For production: Uses real AWS credentials from environment/config.
     * 
     * Spring Cloud Stream automatically configures the SQS connection using:
     * - spring.cloud.stream.bindings.patientEventInput.destination: patient-events-queue
     * - spring.cloud.stream.bindings.patientEventInput.group: monitoring-service-group
     * 
     * The actual SQS client creation and configuration is handled by:
     * - spring-cloud-stream (provides the framework)
     * - SQS Binder (provides the SQS-specific implementation)
     */
    public void init() {
        if (!enabled) {
            log.warn("AWS SQS is disabled");
            return;
        }

        log.info("Configuring AWS SQS for Spring Cloud Stream - Endpoint: {}, Region: {}", endpoint, region);
        log.info("SQS Event Binding: patientEventInput -> patient-events-queue");
        
        // Configuration is externalized via application.yml
        // Spring Cloud Stream automatically detects and configures the connection
    }

}

