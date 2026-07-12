package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.AlertDTO;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for alert operations.
 * Provides endpoints for managing patient alerts and acknowledgments.
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Alerts", description = "Alert management and acknowledgment")
public class AlertController {

    private final AlertService alertService;

    /**
     * Create a new alert.
     *
     * @param patientId the patient UUID
     * @param alertDTO the alert data
     * @return created alert response
     */
    @PostMapping("/patient/{patientId}")
    @Operation(summary = "Create alert", description = "Create a new alert for a patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Alert created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found"),
        @ApiResponse(responseCode = "400", description = "Invalid alert data")
    })
    public ResponseEntity<AlertDTO> createAlert(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId,
            @Valid @RequestBody AlertDTO alertDTO) {
        log.info("Creating alert for patient: {}", patientId);
        AlertDTO created = alertService.createAlert(patientId, alertDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Trigger manual emergency for a patient.
     *
     * @param patientId the patient UUID
     * @return created alert response
     */
    @PostMapping("/patient/{patientId}/emergency")
    @Operation(summary = "Trigger manual emergency", description = "Trigger a manual critical emergency alert for a patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Emergency alert created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<AlertDTO> triggerManualEmergency(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId) {
        log.info("Triggering manual emergency for patient: {}", patientId);
        AlertDTO created = alertService.triggerManualEmergency(patientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get alert by ID.
     *
     * @param id the alert UUID
     * @return alert data
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get alert", description = "Retrieve a specific alert by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alert retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class))),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<AlertDTO> getAlertById(
            @Parameter(description = "Alert UUID", required = true)
            @PathVariable UUID id) {
        log.info("Fetching alert: {}", id);
        AlertDTO alert = alertService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }

    /**
     * Get all unacknowledged alerts.
     *
     * @return list of unacknowledged alerts
     */
    @GetMapping("/unacknowledged")
    @Operation(summary = "Get unacknowledged alerts", description = "Retrieve all unacknowledged alerts in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class)))
    })
    public ResponseEntity<List<AlertDTO>> getUnacknowledgedAlerts() {
        log.info("Fetching all unacknowledged alerts");
        List<AlertDTO> alerts = alertService.getUnacknowledgedAlerts();
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get all unacknowledged critical alerts.
     *
     * @return list of unacknowledged critical alerts
     */
    @GetMapping("/unacknowledged/critical")
    @Operation(summary = "Get unacknowledged critical alerts", description = "Retrieve all unacknowledged CRITICAL severity alerts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class)))
    })
    public ResponseEntity<List<AlertDTO>> getUnacknowledgedCriticalAlerts() {
        log.info("Fetching unacknowledged critical alerts");
        List<AlertDTO> alerts = alertService.getUnacknowledgedCriticalAlerts();
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alerts for a patient.
     *
     * @param patientId the patient UUID
     * @return list of patient alerts
     */
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get patient alerts", description = "Retrieve all alerts for a specific patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<AlertDTO>> getPatientAlerts(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId) {
        log.info("Fetching alerts for patient: {}", patientId);
        List<AlertDTO> alerts = alertService.getPatientAlerts(patientId);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get unacknowledged alerts for a patient.
     *
     * @param patientId the patient UUID
     * @return list of unacknowledged patient alerts
     */
    @GetMapping("/patient/{patientId}/unacknowledged")
    @Operation(summary = "Get patient unacknowledged alerts", description = "Retrieve all unacknowledged alerts for a specific patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<AlertDTO>> getPatientUnacknowledgedAlerts(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId) {
        log.info("Fetching unacknowledged alerts for patient: {}", patientId);
        List<AlertDTO> alerts = alertService.getPatientUnacknowledgedAlerts(patientId);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alerts by severity level.
     *
     * @param severity the alert severity
     * @return list of alerts with that severity
     */
    @GetMapping("/severity/{severity}")
    @Operation(summary = "Get alerts by severity", description = "Retrieve all alerts with a specific severity level")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AlertDTO.class)))
    })
    public ResponseEntity<List<AlertDTO>> getAlertsBySeverity(
            @Parameter(description = "Alert severity (INFO, WARNING, CRITICAL)", required = true)
            @PathVariable AlertSeverity severity) {
        log.info("Fetching alerts with severity: {}", severity);
        List<AlertDTO> alerts = alertService.getAlertsBySeverity(severity);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Acknowledge an alert.
     *
     * @param id the alert UUID
     * @param acknowledgedBy the name of the person acknowledging
     * @return no content response
     */
    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge alert", description = "Mark an alert as acknowledged by medical staff")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Alert acknowledged successfully"),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<Void> acknowledgeAlert(
            @Parameter(description = "Alert UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Name of person acknowledging", required = true)
            @RequestParam String acknowledgedBy) {
        log.info("Acknowledging alert: {} by: {}", id, acknowledgedBy);
        alertService.acknowledgeAlert(id, acknowledgedBy);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get count of unacknowledged critical alerts.
     *
     * @return count of unacknowledged critical alerts
     */
    @GetMapping("/critical/count")
    @Operation(summary = "Count critical alerts", description = "Get count of unacknowledged CRITICAL severity alerts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Count retrieved successfully")
    })
    public ResponseEntity<Long> getUnacknowledgedCriticalAlertCount() {
        log.info("Fetching unacknowledged critical alert count");
        Long count = alertService.getUnacknowledgedCriticalAlertCount();
        return ResponseEntity.ok(count);
    }

}
