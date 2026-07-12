package com.healthgrid.monitoring.dto.hce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaMedicaDTO {
    private String grupo_sanguineo;
    private Double peso_kg;
    private Double altura_cm;
    private String observaciones_generales;
    private Integer id_paciente;
}
