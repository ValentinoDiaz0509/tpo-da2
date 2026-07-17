package com.healthgrid.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * - Handles Java 8 Time API (LocalDateTime, etc.)
 * - Indented output for readability
 * - ISO8601 datetime formatting
 *
 * Used both for the M10 Core event bus payloads and the REST/webhook DTOs.
 */
@Configuration
public class JacksonConfig {

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
