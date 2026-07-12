package com.healthgrid.monitoring;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Patient Monitoring Service.
 * This Spring Boot application manages patient monitoring for hospital systems.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
@OpenAPIDefinition(
    info = @Info(
        title = "Patient Monitoring Service API",
        version = "1.0.0",
        description = "REST API for hospital patient monitoring system with real-time event streaming",
        contact = @Contact(
            name = "HealthGrid Support",
            url = "https://healthgrid.com",
            email = "support@healthgrid.com"
        )
    )
)
public class MonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringServiceApplication.class, args);
    }

}
