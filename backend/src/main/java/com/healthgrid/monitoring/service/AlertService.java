package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.AlertDTO;
import com.healthgrid.monitoring.exception.ApplicationException;
import com.healthgrid.monitoring.exception.ExceptionEnum;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for alert operations.
 * Handles business logic for alert management and acknowledgment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlertService {

    private final AlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final EventPublisherService eventPublisherService;

    /**
     * Create a new alert.
     *
     * @param patientId the patient ID
     * @param alertDTO the alert data
     * @return the created alert DTO
     */
    public AlertDTO createAlert(UUID patientId, AlertDTO alertDTO) {
        log.info("Creating alert for patient ID: {}, Severity: {}", patientId, alertDTO.getSeverity());
        
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        Alert alert = Alert.builder()
            .patient(patient)
            .severity(alertDTO.getSeverity())
            .message(alertDTO.getMessage())
            .acknowledged(false)
            .build();

        Alert savedAlert = alertRepository.save(alert);
        log.info("Alert created successfully with ID: {}", savedAlert.getId());
        
        return convertToDTO(savedAlert);
    }

    /**
     * Trigger a manual emergency intervention for a patient.
     *
     * @param patientId the patient ID
     * @return the created alert DTO
     */
    public AlertDTO triggerManualEmergency(UUID patientId) {
        log.info("Manual emergency triggered for patient ID: {}", patientId);
        
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        Alert alert = Alert.builder()
            .patient(patient)
            .severity(AlertSeverity.CRITICAL)
            .message("Intervención médica solicitada manualmente")
            .acknowledged(false)
            .triggeredAt(LocalDateTime.now(ZoneOffset.UTC))
            .build();

        Alert savedAlert = alertRepository.save(alert);
        log.info("Manual emergency alert created with ID: {}", savedAlert.getId());
        
        // Notify Module 6 (Internación) via event publisher
        try {
            eventPublisherService.publishCriticalAlertEvent(savedAlert, null);
        } catch (Exception e) {
            log.error("Failed to notify Module 6 about manual emergency alert", e);
        }
        
        return convertToDTO(savedAlert);
    }

    /**
     * Get alert by ID.
     *
     * @param id the alert UUID
     * @return the alert DTO
     */
    @Transactional(readOnly = true)
    public AlertDTO getAlertById(UUID id) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.ALERT_NOT_FOUND, id.toString()));
        return convertToDTO(alert);
    }

    /**
     * Get all unacknowledged alerts.
     *
     * @return list of unacknowledged alert DTOs
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalse()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get all unacknowledged critical alerts.
     *
     * @return list of unacknowledged critical alert DTOs
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> getUnacknowledgedCriticalAlerts() {
        return alertRepository.findUnacknowledgedCriticalAlerts()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get alerts for a specific patient.
     *
     * @param patientId the patient ID
     * @return list of alert DTOs for the patient
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> getPatientAlerts(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return alertRepository.findByPatient(patient)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get unacknowledged alerts for a specific patient.
     *
     * @param patientId the patient ID
     * @return list of unacknowledged alert DTOs for the patient
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> getPatientUnacknowledgedAlerts(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return alertRepository.findByPatientAndAcknowledgedFalse(patient)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get alerts by severity level.
     *
     * @param severity the alert severity
     * @return list of alert DTOs with that severity
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> getAlertsBySeverity(AlertSeverity severity) {
        return alertRepository.findBySeverity(severity)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Acknowledge an alert.
     *
     * @param id the alert UUID
     * @param acknowledgedBy the name of the person acknowledging
     */
    public void acknowledgeAlert(UUID id, String acknowledgedBy) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.ALERT_NOT_FOUND, id.toString()));

        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(LocalDateTime.now(ZoneOffset.UTC));
        alertRepository.save(alert);
        log.info("Alert acknowledged with ID: {}, by: {}", id, acknowledgedBy);

        // Notify Module 6 that the alert is resolved
        try {
            eventPublisherService.publishAlertResolvedEvent(alert, "Resuelta por " + acknowledgedBy);
        } catch (Exception e) {
            log.error("Failed to notify Module 6 about resolved alert", e);
        }
    }

    /**
     * Get count of unacknowledged critical alerts.
     *
     * @return count of unacknowledged critical alerts
     */
    @Transactional(readOnly = true)
    public long getUnacknowledgedCriticalAlertCount() {
        return alertRepository.countUnacknowledgedCriticalAlerts();
    }

    /**
     * Convert Alert entity to DTO.
     *
     * @param alert the alert entity
     * @return the alert DTO
     */
    private AlertDTO convertToDTO(Alert alert) {
        return AlertDTO.builder()
            .id(alert.getId())
            .patientId(alert.getPatient().getId())
            .severity(alert.getSeverity())
            .message(alert.getMessage())
            .acknowledged(alert.getAcknowledged())
            .acknowledgedBy(alert.getAcknowledgedBy())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .triggeredAt(alert.getTriggeredAt())
            .build();
    }

}
