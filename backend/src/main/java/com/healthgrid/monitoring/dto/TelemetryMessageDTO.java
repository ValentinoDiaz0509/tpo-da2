package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO representing a telemetry message received from AWS SQS.
 * Contains sensor information, patient ID, vital metrics, and unit metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "TelemetryMessage", description = "Telemetry message from IoT sensor via SQS")
public class TelemetryMessageDTO {

    @JsonProperty("sensor_id")
    @NotBlank(message = "Sensor ID cannot be blank")
    @Schema(description = "Unique identifier of the IoT sensor", example = "SENSOR-ICU-001", required = true)
    private String sensorId;

    @JsonProperty("patient_id")
    @NotNull(message = "Patient ID cannot be null")
    @Schema(description = "UUID of the patient", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    private UUID patientId;

    @JsonProperty("metrics")
    @NotNull(message = "Metrics cannot be null")
    @Valid
    @Schema(description = "Vital signs metrics from the sensor", required = true)
    private TelemetryMetricsDTO metrics;

    @JsonProperty("unit_metadata")
    @Valid
    @Schema(description = "Metadata about the monitoring unit")
    private UnitMetadataDTO unitMetadata;

}
