package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing vital metrics from a telemetry reading.
 * Contains numerical values for heart rate, oxygen saturation, blood pressure, and temperature.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "TelemetryMetrics", description = "Vital signs metrics from sensor")
public class TelemetryMetricsDTO {

    @JsonProperty("heart_rate")
    @Schema(description = "Heart rate in beats per minute (BPM)", example = "85.5")
    private Float heartRate;

    @JsonProperty("spo2")
    @Schema(description = "Oxygen saturation percentage", example = "98.5")
    private Float spO2;

    @JsonProperty("systolic_pressure")
    @Schema(description = "Systolic blood pressure in mmHg", example = "120.0")
    private Float systolicPressure;

    @JsonProperty("diastolic_pressure")
    @Schema(description = "Diastolic blood pressure in mmHg", example = "80.0")
    private Float diastolicPressure;

    @JsonProperty("temperature")
    @Schema(description = "Body temperature in Celsius", example = "37.2")
    private Float temperature;

}
