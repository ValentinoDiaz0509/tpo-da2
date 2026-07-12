package com.healthgrid.monitoring.dto.hce;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientHceSummaryDTO {
    private FichaMedicaDTO fichaMedica;
    private List<AntecedenteDTO> antecedentes;
    
    public boolean hasAntecedenteOrObservacion(String keyword) {
        String kw = keyword.toLowerCase();
        
        // Check in observaciones_generales
        if (fichaMedica != null && fichaMedica.getObservaciones_generales() != null) {
            if (fichaMedica.getObservaciones_generales().toLowerCase().contains(kw)) {
                return true;
            }
        }
        
        // Check in antecedentes descriptions
        if (antecedentes != null) {
            for (AntecedenteDTO ant : antecedentes) {
                if (ant.getDescripcion() != null && ant.getDescripcion().toLowerCase().contains(kw)) {
                    return true;
                }
                if (ant.getObservaciones() != null && ant.getObservaciones().toLowerCase().contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }
}
