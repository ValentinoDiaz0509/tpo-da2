package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaEmergenciaRequestDTO {
    @JsonProperty("paciente_id")
    private Long pacienteId;
    
    private LocalDateTime timestamp;
    
    private String observaciones;
}
