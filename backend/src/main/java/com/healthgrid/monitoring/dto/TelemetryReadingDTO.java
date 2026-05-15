package com.healthgrid.monitoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for TelemetryReading.
 * Contains vital signs and metrics for a patient at a specific time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Telemetry Reading DTO - Patient vital signs and metrics")
public class TelemetryReadingDTO {

    @Schema(description = "Telemetry reading unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @NotNull(message = "Patient ID cannot be null")
    @Schema(description = "Patient unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID patientId;

    @NotNull(message = "Heart rate cannot be null")
    @Schema(description = "Heart rate in BPM (beats per minute)", example = "72.5")
    private Float heartRate;

    @NotNull(message = "SpO2 cannot be null")
    @Schema(description = "Oxygen saturation percentage", example = "98.5")
    private Float spO2;

    @NotNull(message = "Systolic pressure cannot be null")
    @Schema(description = "Systolic blood pressure in mmHg", example = "120.0")
    private Float systolicPressure;

    @NotNull(message = "Diastolic pressure cannot be null")
    @Schema(description = "Diastolic blood pressure in mmHg", example = "80.0")
    private Float diastolicPressure;

    @NotNull(message = "Temperature cannot be null")
    @Schema(description = "Body temperature in Celsius", example = "37.2")
    private Float temperature;

    @Schema(description = "Timestamp when the reading was recorded")
    private LocalDateTime recordedAt;

}
