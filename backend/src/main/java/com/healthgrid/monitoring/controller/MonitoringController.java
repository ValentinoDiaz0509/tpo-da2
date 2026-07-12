package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.PagedPatientMonitoringResponseDTO;
import com.healthgrid.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patients/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Patient monitoring endpoints")
@Slf4j
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping
    @Operation(summary = "Get paginated patients monitoring data",
               description = "Returns patients with latest metrics and active alerts, with search and pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Monitoring data retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = PagedPatientMonitoringResponseDTO.class)))
    })
    public ResponseEntity<PagedPatientMonitoringResponseDTO> getPatientMonitoring(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "severity,desc") String sort) {
        try {
            PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
                page, size, search, sort);

            log.info("Retrieved monitoring page {} (size {}) with {} patients",
                response.getPage(), response.getSize(), response.getContent().size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving patient monitoring data", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
