package com.healthgrid.monitoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.healthgrid.monitoring.model.PatientStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Patient.
 * Used for API requests and responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Patient Data Transfer Object")
public class PatientDTO {

    @Schema(description = "Patient unique identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @NotBlank(message = "Name cannot be blank")
    @Schema(description = "Patient's full name", example = "Juan Pérez")
    private String name;

    @NotBlank(message = "Room number cannot be blank")
    @Schema(description = "Hospital room number", example = "101")
    private String room;

    @NotBlank(message = "Bed number cannot be blank")
    @Schema(description = "Hospital bed number", example = "A")
    private String bed;

    @NotNull(message = "Status cannot be null")
    @Schema(description = "Patient health status", example = "NORMAL", allowableValues = {"NORMAL", "WARNING", "CRITICAL"})
    private PatientStatus status;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

}
