package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.PagedPatientMonitoringResponseDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.PatientSeverityRank;
import com.healthgrid.monitoring.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private TelemetryReadingRepository telemetryReadingRepository;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private MonitoringService monitoringService;

    private Patient criticalPatient;
    private Patient warningPatient;
    private Patient normalPatient;

    @BeforeEach
    void setUp() {
        criticalPatient = Patient.builder()
            .id(UUID.randomUUID())
            .name("Harrison, E.")
            .room("UTI")
            .bed("Cama 04")
            .status(PatientStatus.CRITICAL)
            .build();

        warningPatient = Patient.builder()
            .id(UUID.randomUUID())
            .name("Chen, W.")
            .room("Sala General")
            .bed("Cama 07")
            .status(PatientStatus.WARNING)
            .build();

        normalPatient = Patient.builder()
            .id(UUID.randomUUID())
            .name("Miller, A.")
            .room("Sala General")
            .bed("Cama 01")
            .status(PatientStatus.NORMAL)
            .build();
    }

    @Test
    void shouldReturnPaginatedPatients() {
        Page<Patient> page = new PageImpl<>(
            List.of(criticalPatient, warningPatient),
            PageRequest.of(0, 2),
            3);

        when(patientRepository.findAllSortedBySeverity(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of(
            severityRank(criticalPatient.getId(), 3),
            severityRank(warningPatient.getId(), 2),
            severityRank(normalPatient.getId(), 1)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 2, null, "severity,desc");

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getContent()).hasSize(2);
    }

    @Test
    void shouldSearchByPatientName() {
        Page<Patient> page = new PageImpl<>(List.of(criticalPatient), PageRequest.of(0, 12), 1);

        when(patientRepository.findFilteredSortedBySeverity(eq("Harrison"), any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findPatientSeverityRanks("Harrison")).thenReturn(List.of(
            severityRank(criticalPatient.getId(), 3)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, "Harrison", "severity,desc");

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPatientName()).contains("Harrison");
    }

    @Test
    void shouldSearchByBedNumber() {
        Page<Patient> page = new PageImpl<>(List.of(criticalPatient), PageRequest.of(0, 12), 1);

        when(patientRepository.findFilteredSortedBySeverity(eq("Cama 04"), any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findPatientSeverityRanks("Cama 04")).thenReturn(List.of(
            severityRank(criticalPatient.getId(), 3)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, "Cama 04", "severity,desc");

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBed()).isEqualTo("Cama 04");
    }

    @Test
    void shouldSortCriticalPatientsFirst() {
        Page<Patient> page = new PageImpl<>(
            List.of(criticalPatient, warningPatient, normalPatient),
            PageRequest.of(0, 12),
            3);

        when(patientRepository.findAllSortedBySeverity(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of(
            severityRank(criticalPatient.getId(), 3),
            severityRank(warningPatient.getId(), 2),
            severityRank(normalPatient.getId(), 1)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, null, "severity,desc");

        assertThat(response.getContent().get(0).getStatus()).isEqualTo("CRITICAL");
        assertThat(response.getContent().get(0).getPatientName()).contains("Harrison");
    }

    @Test
    void shouldReportHiddenAlertsOutsideCurrentPage() {
        Page<Patient> page = new PageImpl<>(
            List.of(criticalPatient, warningPatient),
            PageRequest.of(0, 2),
            3);

        when(patientRepository.findAllSortedBySeverity(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of(
            severityRank(criticalPatient.getId(), 3),
            severityRank(warningPatient.getId(), 2),
            severityRank(normalPatient.getId(), 1)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 2, null, "severity,desc");

        assertThat(response.getHiddenAlertsSummary().getCriticalCount()).isZero();
        assertThat(response.getHiddenAlertsSummary().getWarningCount()).isZero();
    }

    @Test
    void shouldUseActiveAlertsForPatientStatus() {
        Page<Patient> page = new PageImpl<>(List.of(normalPatient), PageRequest.of(0, 12), 1);
        Alert criticalAlert = Alert.builder()
            .id(UUID.randomUUID())
            .patient(normalPatient)
            .severity(AlertSeverity.CRITICAL)
            .message("Alert: heart_rate value (130.00) triggered")
            .acknowledged(false)
            .build();

        when(patientRepository.findAllSortedBySeverity(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of(criticalAlert));
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of(
            severityRank(normalPatient.getId(), 1)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, null, "severity,desc");

        assertThat(response.getContent().get(0).getStatus()).isEqualTo("CRITICAL");
        assertThat(response.getContent().get(0).getActiveAlerts()).hasSize(1);
    }

    @Test
    void shouldSortByNameWhenRequested() {
        Page<Patient> page = new PageImpl<>(
            List.of(normalPatient, criticalPatient),
            PageRequest.of(0, 12, Sort.by(Sort.Direction.ASC, "name")),
            2);

        when(patientRepository.findAllActive(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of());

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, null, "name,asc");

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getPatientName()).contains("Miller");
    }

    @Test
    void shouldUseAscendingSeveritySort() {
        Page<Patient> page = new PageImpl<>(List.of(normalPatient), PageRequest.of(0, 12), 1);

        when(patientRepository.findAllSortedBySeverityAsc(any(Pageable.class))).thenReturn(page);
        when(telemetryReadingRepository.findLatestReadingsForPatients(any())).thenReturn(List.of());
        when(alertRepository.findByPatientInAndAcknowledgedFalse(any())).thenReturn(List.of());
        when(patientRepository.findAllPatientSeverityRanks()).thenReturn(List.of(
            severityRank(normalPatient.getId(), 1)
        ));

        PagedPatientMonitoringResponseDTO response = monitoringService.getPatientMonitoring(
            0, 12, null, "severity,asc");

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPatientName()).contains("Miller");
    }

    private PatientSeverityRank severityRank(UUID patientId, int rank) {
        return new PatientSeverityRank() {
            @Override
            public UUID getPatientId() {
                return patientId;
            }

            @Override
            public Integer getSeverityRank() {
                return rank;
            }
        };
    }
}
