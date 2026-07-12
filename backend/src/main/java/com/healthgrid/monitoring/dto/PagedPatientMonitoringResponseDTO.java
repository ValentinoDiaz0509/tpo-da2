package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PagedPatientMonitoringResponseDTO {

    private List<PatientMonitoringDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private HiddenAlertsSummaryDTO hiddenAlertsSummary;
}
