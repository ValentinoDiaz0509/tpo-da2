package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientStatusCalculator {

    private final AlertRepository alertRepository;

    public PatientStatus calculatePatientStatus(Patient patient) {
        return calculatePatientStatus(patient.getId(), patient);
    }

    public PatientStatus calculatePatientStatus(UUID patientId, Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient is required to calculate status");
        }

        if (patientId != null && !patientId.equals(patient.getId())) {
            log.debug("Patient ID mismatch while calculating status. Using entity ID {}", patient.getId());
        }

        List<Alert> unacknowledgedAlerts = alertRepository.findByPatientAndAcknowledgedFalse(patient);
        if (unacknowledgedAlerts.isEmpty()) {
            return patient.getStatus();
        }

        return unacknowledgedAlerts.stream()
            .map(Alert::getSeverity)
            .map(this::mapSeverityToPatientStatus)
            .max(Comparator.comparingInt(PatientStatus::getPriority))
            .orElse(patient.getStatus());
    }

    private PatientStatus mapSeverityToPatientStatus(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> PatientStatus.CRITICAL;
            case WARNING -> PatientStatus.WARNING;
            case INFO -> PatientStatus.NORMAL;
        };
    }
}
