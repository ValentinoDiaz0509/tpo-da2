package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing unit metadata associated with a telemetry reading.
 * Contains information about the monitoring device/unit location.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "UnitMetadata", description = "Monitoring unit metadata")
public class UnitMetadataDTO {

    @JsonProperty("unit_id")
    @Schema(description = "Unique identifier of the monitoring unit", example = "UNIT-001")
    private String unitId;

    @JsonProperty("unit_location")
    @Schema(description = "Physical location of the monitoring unit", example = "ICU-Room-101")
    private String unitLocation;

    @JsonProperty("device_model")
    @Schema(description = "Model of the medical device", example = "Philips IntelliVue")
    private String deviceModel;

    @JsonProperty("device_serial")
    @Schema(description = "Serial number of the device", example = "SN-789456")
    private String deviceSerial;

    @JsonProperty("firmware_version")
    @Schema(description = "Firmware version of the monitoring device", example = "2.1.5")
    private String firmwareVersion;

}
