package com.healthgrid.monitoring.exception;

import lombok.Getter;

@Getter
public enum ExceptionEnum {

    PATIENT_NOT_FOUND("PATIENT_NOT_FOUND", "Patient not found with ID: %s"),
    ALERT_NOT_FOUND("ALERT_NOT_FOUND", "Alert not found with ID: %s"),
    RULE_NOT_FOUND("RULE_NOT_FOUND", "Rule not found with ID: %s"),
    TELEMETRY_READING_NOT_FOUND("TELEMETRY_READING_NOT_FOUND", "No telemetry readings found for patient ID: %s"),
    VALIDATION_ERROR("VALIDATION_ERROR", "Request validation failed"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Unexpected internal server error");

    private final String key;
    private final String message;

    ExceptionEnum(String key, String message) {
        this.key = key;
        this.message = message;
    }
}
