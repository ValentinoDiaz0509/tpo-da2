package com.healthgrid.monitoring.dto.hce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntecedenteDTO {
    private Integer id;
    private Integer id_paciente;
    private String tipo;
    private String descripcion;
    private String fecha_suceso;
    private String observaciones;
}
