package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LatestMetricsDTO {

    @JsonProperty("heart_rate")
    private MetricDTO heartRate;

    @JsonProperty("spo2")
    private MetricDTO spO2;

    @JsonProperty("systolic_pressure")
    private MetricDTO systolicPressure;

    @JsonProperty("diastolic_pressure")
    private MetricDTO diastolicPressure;

    @JsonProperty("temperature")
    private MetricDTO temperature;
}
