package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.AlertSummaryDTO;
import com.healthgrid.monitoring.dto.LatestMetricsDTO;
import com.healthgrid.monitoring.dto.MetricDTO;
import com.healthgrid.monitoring.dto.PatientMonitoringDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.TelemetryReadingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Monitoring Controller for Dashboard API.
 * 
 * Provides real-time and historical patient monitoring data
 * for the React dashboard.
 * 
 * Endpoints:
 * - GET /patients/monitoring - Get all patients with current metrics and alerts
 */
@RestController
@RequestMapping("/patients/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Patient monitoring endpoints")
@Slf4j
public class MonitoringController {

    private final PatientRepository patientRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AlertRepository alertRepository;
    
    /**
     * GET /api/v1/patients/monitoring
     * Retorna lista de pacientes con datos de monitoreo en tiempo real.
     */
    @GetMapping
    @Operation(summary = "Get all patients monitoring data",
               description = "Returns all patients with latest metrics and active alerts")
    public ResponseEntity<List<PatientMonitoringDTO>> getPatientMonitoring() {
        try {
            List<Patient> patients = patientRepository.findAll();
            
            List<PatientMonitoringDTO> response = patients.stream()
                .map(this::buildPatientMonitoringDTO)
                .collect(Collectors.toList());
            
            log.info("✓ Retrieved monitoring data for {} patients", response.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving patient monitoring data", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Construye PatientMonitoringDTO para un paciente específico.
     * Valida que todos los campos requeridos estén presentes.
     */
    private PatientMonitoringDTO buildPatientMonitoringDTO(Patient patient) {
        // PASO 1: Obtener última lectura de telemetría
        Optional<TelemetryReading> latestReading = 
            Optional.ofNullable(telemetryReadingRepository.findLatestReadingForPatient(patient));
        
        // PASO 2: Construir LatestMetricsDTO (puede ser parcial)
        LatestMetricsDTO latestMetrics = latestReading
            .map(this::buildLatestMetricsDTO)
            .orElse(buildEmptyMetricsDTO()); // Si NO hay lectura → retornar vacío
        
        // PASO 3: Obtener alertas sin reconocer
        List<Alert> activeAlerts = alertRepository.findByPatientAndAcknowledgedFalse(patient);
        
        List<AlertSummaryDTO> alertSummaries = activeAlerts.stream()
            .map(this::buildAlertSummaryDTO)
            .collect(Collectors.toList());
        
        // PASO 4: Determinar status general basado en alertas
        String status = determinePatientStatus(patient, activeAlerts);
        
        // PASO 5: Validar respuesta antes de retornar
        PatientMonitoringDTO dto = PatientMonitoringDTO.builder()
            .patientId(patient.getId())
            .patientName(patient.getName())
            .room(patient.getRoom())
            .bed(patient.getBed())
            .status(status)
            .latestMetrics(latestMetrics)
            .activeAlerts(alertSummaries)
            .lastUpdate(LocalDateTime.now())
            .build();
        
        validateMonitoringDTO(dto);
        return dto;
    }
    
    /**
     * Construye LatestMetricsDTO a partir de una TelemetryReading.
     */
    private LatestMetricsDTO buildLatestMetricsDTO(TelemetryReading reading) {
        return LatestMetricsDTO.builder()
            .heartRate(buildMetricDTO(
                reading.getHeartRate(),
                "bpm",
                "heart_rate",
                60.0, 100.0, // normal range
                100.0 // warning threshold
            ))
            .spO2(buildMetricDTO(
                reading.getSpO2(),
                "%",
                "spo2",
                95.0, 100.0,
                94.0
            ))
            .systolicPressure(buildMetricDTO(
                reading.getSystolicPressure(),
                "mmHg",
                "systolic_pressure",
                90.0, 120.0,
                140.0
            ))
            .diastolicPressure(buildMetricDTO(
                reading.getDiastolicPressure(),
                "mmHg",
                "diastolic_pressure",
                60.0, 80.0,
                95.0
            ))
            .temperature(buildMetricDTO(
                reading.getTemperature(),
                "°C",
                "temperature",
                36.5, 37.5,
                38.5
            ))
            .build();
    }
    
    /**
     * Construye un MetricDTO con validaciones de rango.
     */
    private MetricDTO buildMetricDTO(
            Float value,
            String unit,
            String metricName,
            Double normalMin,
            Double normalMax,
            Double warningThreshold) {
        
        // Validar que el valor no sea null
        if (value == null) {
            return MetricDTO.builder()
                .value(null)
                .unit(unit)
                .status("UNKNOWN")
                .ruleThreshold(warningThreshold)
                .build();
        }
        
        // Determinar status basado en rango
        String status = determineMetricStatus(value, normalMin, normalMax, warningThreshold);
        
        return MetricDTO.builder()
            .value(value)
            .unit(unit)
            .status(status)
            .timestamp(LocalDateTime.now())
            .ruleThreshold(warningThreshold)
            .build();
    }
    
    /**
     * Retorna un LatestMetricsDTO vacío cuando NO hay lecturas.
     */
    private LatestMetricsDTO buildEmptyMetricsDTO() {
        MetricDTO empty = MetricDTO.builder()
            .value(null)
            .status("NO_DATA")
            .build();
        
        return LatestMetricsDTO.builder()
            .heartRate(empty)
            .spO2(empty)
            .systolicPressure(empty)
            .diastolicPressure(empty)
            .temperature(empty)
            .build();
    }
    
    /**
     * Construye AlertSummaryDTO a partir de una Alert.
     */
    private AlertSummaryDTO buildAlertSummaryDTO(Alert alert) {
        String metricName = extractMetricNameFromMessage(alert.getMessage());
        Double metricValue = extractMetricValueFromMessage(alert.getMessage());
        
        return AlertSummaryDTO.builder()
            .alertId(alert.getId())
            .severity(alert.getSeverity().name())
            .message(alert.getMessage())
            .triggeredAt(alert.getTriggeredAt())
            .metricName(metricName)
            .metricValue(metricValue)
            .build();
    }
    
    /**
     * Determina el status general del paciente basado en alertas activas.
     * Lógica: CRITICAL > WARNING > NORMAL
     */
    private String determinePatientStatus(Patient patient, List<Alert> activeAlerts) {
        if (activeAlerts.isEmpty()) {
            return patient.getStatus().name();
        }
        
        // Checar si hay alertas CRITICAL
        boolean hasCritical = activeAlerts.stream()
            .anyMatch(a -> AlertSeverity.CRITICAL.equals(a.getSeverity()));
        
        if (hasCritical) {
            return "CRITICAL";
        }
        
        // Checar si hay alertas WARNING
        boolean hasWarning = activeAlerts.stream()
            .anyMatch(a -> AlertSeverity.WARNING.equals(a.getSeverity()));
        
        if (hasWarning) {
            return "WARNING";
        }
        
        return patient.getStatus().name();
    }
    
    /**
     * Determina el status de una métrica individual.
     */
    private String determineMetricStatus(
            Float value,
            Double normalMin,
            Double normalMax,
            Double warningThreshold) {
        
        if (value >= normalMin && value <= normalMax) {
            return "NORMAL";
        }
        
        // Revisar si está en zona de warning
        if (Math.abs(value - normalMax) < 10 || Math.abs(value - normalMin) < 10) {
            return "WARNING";
        }
        
        return "CRITICAL";
    }
    
    /**
     * Extrae el nombre de métrica desde el mensaje de alerta.
     * Patrón: "Alert: heart_rate value (XXX) triggered..."
     */
    private String extractMetricNameFromMessage(String message) {
        Pattern pattern = Pattern.compile("Alert: (\\w+)");
        Matcher matcher = pattern.matcher(message);
        
        return matcher.find() ? matcher.group(1) : "unknown";
    }
    
    /**
     * Extrae el valor de métrica desde el mensaje.
     * Patrón: "value (XXX.XX)"
     */
    private Double extractMetricValueFromMessage(String message) {
        Pattern pattern = Pattern.compile("value \\(([\\d.]+)\\)");
        Matcher matcher = pattern.matcher(message);
        
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }
    
    /**
     * Valida que la respuesta MonitoringDTO sea válida.
     */
    private void validateMonitoringDTO(PatientMonitoringDTO dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        
        if (StringUtils.isBlank(dto.getPatientName())) {
            throw new IllegalArgumentException("Patient name is required");
        }
        
        if (dto.getLatestMetrics() == null) {
            throw new IllegalArgumentException("Latest metrics cannot be null");
        }
        
        if (dto.getActiveAlerts() == null) {
            throw new IllegalArgumentException("Active alerts cannot be null");
        }
        
        log.debug("✓ Monitoring DTO validation passed for patient: {}", dto.getPatientId());
    }
}
