package com.healthgrid.monitoring;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 * Provides common test configuration for Spring Boot integration tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class BaseIntegrationTest {
    // Common test setup and configuration
}
