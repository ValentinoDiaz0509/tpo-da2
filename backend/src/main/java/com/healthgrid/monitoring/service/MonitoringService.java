package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.AlertSummaryDTO;
import com.healthgrid.monitoring.dto.HiddenAlertsSummaryDTO;
import com.healthgrid.monitoring.dto.LatestMetricsDTO;
import com.healthgrid.monitoring.dto.MetricDTO;
import com.healthgrid.monitoring.dto.PagedPatientMonitoringResponseDTO;
import com.healthgrid.monitoring.dto.PatientMonitoringDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.PatientSeverityRank;
import com.healthgrid.monitoring.repository.TelemetryReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int SEVERITY_CRITICAL = 3;
    private static final int SEVERITY_WARNING = 2;

    private final PatientRepository patientRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public PagedPatientMonitoringResponseDTO getPatientMonitoring(
            int page,
            int size,
            String search,
            String sort) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedSearch = StringUtils.trimToNull(search);
        Pageable pageable = resolvePageable(safePage, safeSize, sort);

        Page<Patient> patientPage = resolvePatientPage(normalizedSearch, sort, pageable);
        List<Patient> patients = patientPage.getContent();

        List<PatientMonitoringDTO> content = buildPatientMonitoringDTOs(patients);
        Set<UUID> visiblePatientIds = patients.stream()
            .map(Patient::getId)
            .collect(Collectors.toSet());

        HiddenAlertsSummaryDTO hiddenSummary = buildHiddenAlertsSummary(normalizedSearch, visiblePatientIds);

        return PagedPatientMonitoringResponseDTO.builder()
            .content(content)
            .page(patientPage.getNumber())
            .size(patientPage.getSize())
            .totalElements(patientPage.getTotalElements())
            .totalPages(patientPage.getTotalPages())
            .hiddenAlertsSummary(hiddenSummary)
            .build();
    }

    private Pageable resolvePageable(int safePage, int safeSize, String sort) {
        if (isSeveritySort(sort)) {
            return PageRequest.of(safePage, safeSize);
        }
        return PageRequest.of(safePage, safeSize, parseFieldSort(sort));
    }

    private Page<Patient> resolvePatientPage(String search, String sort, Pageable pageable) {
        if (isSeveritySort(sort)) {
            if (isSortAscending(sort)) {
                return patientRepository.findFilteredSortedBySeverityAsc(search, pageable);
            }
            return patientRepository.findFilteredSortedBySeverity(search, pageable);
        }
        return patientRepository.findFilteredBySearch(search, pageable);
    }

    private Sort parseFieldSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim().toLowerCase();
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;

        String property = switch (field) {
            case "name", "room", "bed" -> field;
            default -> "name";
        };

        return Sort.by(direction, property);
    }

    private boolean isSortAscending(String sort) {
        if (StringUtils.isBlank(sort)) {
            return false;
        }
        String[] parts = sort.split(",", 2);
        return parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
    }

    private boolean isSeveritySort(String sort) {
        if (StringUtils.isBlank(sort)) {
            return true;
        }
        String normalized = sort.trim().toLowerCase();
        return normalized.startsWith("severity");
    }

    private HiddenAlertsSummaryDTO buildHiddenAlertsSummary(String search, Set<UUID> visiblePatientIds) {
        List<PatientSeverityRank> severityRanks = patientRepository.findPatientSeverityRanks(search);

        long criticalCount = 0;
        long warningCount = 0;

        for (PatientSeverityRank rank : severityRanks) {
            if (visiblePatientIds.contains(rank.getPatientId())) {
                continue;
            }
            Integer severity = rank.getSeverityRank();
            if (severity == null) {
                continue;
            }
            if (severity >= SEVERITY_CRITICAL) {
                criticalCount++;
            } else if (severity >= SEVERITY_WARNING) {
                warningCount++;
            }
        }

        return HiddenAlertsSummaryDTO.builder()
            .criticalCount(criticalCount)
            .warningCount(warningCount)
            .build();
    }

    private List<PatientMonitoringDTO> buildPatientMonitoringDTOs(List<Patient> patients) {
        if (patients.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> patientIds = patients.stream().map(Patient::getId).toList();

        Map<UUID, TelemetryReading> latestReadingsByPatientId = telemetryReadingRepository
            .findLatestReadingsForPatients(patientIds)
            .stream()
            .collect(Collectors.toMap(reading -> reading.getPatient().getId(), reading -> reading, (a, b) -> a));

        Map<UUID, List<Alert>> alertsByPatientId = alertRepository
            .findByPatientInAndAcknowledgedFalse(patients)
            .stream()
            .collect(Collectors.groupingBy(alert -> alert.getPatient().getId()));

        return patients.stream()
            .map(patient -> buildPatientMonitoringDTO(
                patient,
                latestReadingsByPatientId.get(patient.getId()),
                alertsByPatientId.getOrDefault(patient.getId(), Collections.emptyList())))
            .toList();
    }

    private PatientMonitoringDTO buildPatientMonitoringDTO(
            Patient patient,
            TelemetryReading latestReading,
            List<Alert> activeAlerts) {

        LatestMetricsDTO latestMetrics = Optional.ofNullable(latestReading)
            .map(this::buildLatestMetricsDTO)
            .orElseGet(this::buildEmptyMetricsDTO);

        List<AlertSummaryDTO> alertSummaries = activeAlerts.stream()
            .map(this::buildAlertSummaryDTO)
            .toList();

        String status = determinePatientStatus(patient, activeAlerts);

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

    private LatestMetricsDTO buildLatestMetricsDTO(TelemetryReading reading) {
        return LatestMetricsDTO.builder()
            .heartRate(buildMetricDTO(
                reading.getHeartRate(), "bpm", "heart_rate", 60.0, 100.0, 100.0))
            .spO2(buildMetricDTO(
                reading.getSpO2(), "%", "spo2", 95.0, 100.0, 94.0))
            .systolicPressure(buildMetricDTO(
                reading.getSystolicPressure(), "mmHg", "systolic_pressure", 90.0, 120.0, 140.0))
            .diastolicPressure(buildMetricDTO(
                reading.getDiastolicPressure(), "mmHg", "diastolic_pressure", 60.0, 80.0, 95.0))
            .temperature(buildMetricDTO(
                reading.getTemperature(), "°C", "temperature", 36.5, 37.5, 38.5))
            .build();
    }

    private MetricDTO buildMetricDTO(
            Float value,
            String unit,
            String metricName,
            Double normalMin,
            Double normalMax,
            Double warningThreshold) {

        if (value == null) {
            return MetricDTO.builder()
                .value(null)
                .unit(unit)
                .status("UNKNOWN")
                .ruleThreshold(warningThreshold)
                .build();
        }

        String status = determineMetricStatus(value, normalMin, normalMax, warningThreshold);

        return MetricDTO.builder()
            .value(value)
            .unit(unit)
            .status(status)
            .timestamp(LocalDateTime.now())
            .ruleThreshold(warningThreshold)
            .build();
    }

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

    private String determinePatientStatus(Patient patient, List<Alert> activeAlerts) {
        if (activeAlerts.isEmpty()) {
            return patient.getStatus().name();
        }

        boolean hasCritical = activeAlerts.stream()
            .anyMatch(a -> AlertSeverity.CRITICAL.equals(a.getSeverity()));

        if (hasCritical) {
            return "CRITICAL";
        }

        boolean hasWarning = activeAlerts.stream()
            .anyMatch(a -> AlertSeverity.WARNING.equals(a.getSeverity()));

        if (hasWarning) {
            return "WARNING";
        }

        return patient.getStatus().name();
    }

    private String determineMetricStatus(
            Float value,
            Double normalMin,
            Double normalMax,
            Double warningThreshold) {

        if (value >= normalMin && value <= normalMax) {
            return "NORMAL";
        }

        if (Math.abs(value - normalMax) < 10 || Math.abs(value - normalMin) < 10) {
            return "WARNING";
        }

        return "CRITICAL";
    }

    private String extractMetricNameFromMessage(String message) {
        Pattern pattern = Pattern.compile("Alert: (\\w+)");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private Double extractMetricValueFromMessage(String message) {
        Pattern pattern = Pattern.compile("value \\(([\\d.]+)\\)");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

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
    }

    public static int getDefaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }
}
