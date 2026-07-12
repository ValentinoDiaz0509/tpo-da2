package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.TelemetryReadingDTO;
import com.healthgrid.monitoring.service.TelemetryReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for telemetry reading operations.
 * Provides endpoints for recording and retrieving patient vital signs data.
 */
@RestController
@RequestMapping("/telemetry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Telemetry", description = "Patient telemetry reading management")
public class TelemetryReadingController {

    private final TelemetryReadingService telemetryReadingService;

    /**
     * Record a new telemetry reading for a patient.
     *
     * @param patientId the patient UUID
     * @param readingDTO the telemetry reading data
     * @return created reading response
     */
    @PostMapping("/patient/{patientId}")
    @Operation(summary = "Record telemetry reading", description = "Submit a new telemetry reading for a patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Reading recorded successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found"),
        @ApiResponse(responseCode = "400", description = "Invalid reading data")
    })
    public ResponseEntity<TelemetryReadingDTO> recordReading(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId,
            @Valid @RequestBody TelemetryReadingDTO readingDTO) {
        log.info("Recording telemetry for patient: {}", patientId);
        TelemetryReadingDTO created = telemetryReadingService.recordReading(patientId, readingDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get the latest telemetry reading for a patient.
     *
     * @param patientId the patient UUID
     * @return latest reading
     */
    @GetMapping("/patient/{patientId}/latest")
    @Operation(summary = "Get latest reading", description = "Retrieve the most recent telemetry reading for a patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reading retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient or reading not found")
    })
    public ResponseEntity<TelemetryReadingDTO> getLatestReading(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId) {
        log.info("Fetching latest telemetry for patient: {}", patientId);
        TelemetryReadingDTO reading = telemetryReadingService.getLatestReading(patientId);
        return ResponseEntity.ok(reading);
    }

    /**
     * Get all telemetry readings for a patient.
     *
     * @param patientId the patient UUID
     * @return list of readings
     */
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get patient readings", description = "Retrieve all telemetry readings for a patient")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<TelemetryReadingDTO>> getPatientReadings(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId) {
        log.info("Fetching all telemetry for patient: {}", patientId);
        List<TelemetryReadingDTO> readings = telemetryReadingService.getPatientReadings(patientId);
        return ResponseEntity.ok(readings);
    }

    /**
     * Get telemetry readings for a patient within a time range.
     *
     * @param patientId the patient UUID
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @return list of readings in time range
     */
    @GetMapping("/patient/{patientId}/range")
    @Operation(summary = "Get readings by time range", description = "Retrieve telemetry readings for a patient within a specific time period")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<TelemetryReadingDTO>> getReadingsByTimeRange(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId,
            @Parameter(description = "Start time (ISO-8601 format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "End time (ISO-8601 format)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("Fetching telemetry for patient {} between {} and {}", patientId, startTime, endTime);
        List<TelemetryReadingDTO> readings = telemetryReadingService.getReadingsByTimeRange(patientId, startTime, endTime);
        return ResponseEntity.ok(readings);
    }

    /**
     * Get telemetry readings with high heart rate for a patient.
     *
     * @param patientId the patient UUID
     * @param threshold the heart rate threshold
     * @return list of readings with high heart rate
     */
    @GetMapping("/patient/{patientId}/high-heart-rate")
    @Operation(summary = "Get high heart rate readings", description = "Retrieve telemetry readings where heart rate exceeds threshold")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<TelemetryReadingDTO>> getHighHeartRateReadings(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId,
            @Parameter(description = "Heart rate threshold (BPM)", required = true)
            @RequestParam Float threshold) {
        log.info("Fetching high heart rate readings for patient: {} with threshold: {}", patientId, threshold);
        List<TelemetryReadingDTO> readings = telemetryReadingService.getHighHeartRateReadings(patientId, threshold);
        return ResponseEntity.ok(readings);
    }

    /**
     * Get telemetry readings with low SpO2 for a patient.
     *
     * @param patientId the patient UUID
     * @param threshold the SpO2 threshold
     * @return list of readings with low SpO2
     */
    @GetMapping("/patient/{patientId}/low-spo2")
    @Operation(summary = "Get low SpO2 readings", description = "Retrieve telemetry readings where SpO2 is below threshold")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Readings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryReadingDTO.class))),
        @ApiResponse(responseCode = "404", description = "Patient not found")
    })
    public ResponseEntity<List<TelemetryReadingDTO>> getLowSpO2Readings(
            @Parameter(description = "Patient UUID", required = true)
            @PathVariable UUID patientId,
            @Parameter(description = "SpO2 threshold (%)", required = true)
            @RequestParam Float threshold) {
        log.info("Fetching low SpO2 readings for patient: {} with threshold: {}", patientId, threshold);
        List<TelemetryReadingDTO> readings = telemetryReadingService.getLowSpO2Readings(patientId, threshold);
        return ResponseEntity.ok(readings);
    }

}
