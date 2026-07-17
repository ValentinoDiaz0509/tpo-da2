package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Alert entity operations.
 * Provides data access layer for patient alerts.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /**
     * Find all alerts for a specific patient.
     *
     * @param patient the patient
     * @return list of alerts for the patient
     */
    List<Alert> findByPatient(Patient patient);

    /**
     * Find all unacknowledged alerts.
     *
     * @return list of alerts not yet acknowledged
     */
    List<Alert> findByAcknowledgedFalse();

    /**
     * Find all acknowledged alerts.
     *
     * @return list of alerts that have been acknowledged
     */
    List<Alert> findByAcknowledgedTrue();

    /**
     * Find all alerts by severity level.
     *
     * @param severity the alert severity
     * @return list of alerts with that severity
     */
    List<Alert> findBySeverity(AlertSeverity severity);

    /**
     * Find all unacknowledged alerts for a specific patient.
     *
     * @param patient the patient
     * @return list of unacknowledged alerts for the patient
     */
    List<Alert> findByPatientAndAcknowledgedFalse(Patient patient);

    /**
     * Find critical alerts that haven't been acknowledged.
     *
     * @return list of unacknowledged critical alerts
     */
    @Query("SELECT a FROM Alert a WHERE a.severity = 'CRITICAL' AND a.acknowledged = false ORDER BY a.triggeredAt DESC")
    List<Alert> findUnacknowledgedCriticalAlerts();

    /**
     * Find alerts for a patient within a time range.
     *
     * @param patient the patient
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @return list of alerts within the time range
     */
    @Query("SELECT a FROM Alert a WHERE a.patient = :patient AND a.triggeredAt BETWEEN :startTime AND :endTime ORDER BY a.triggeredAt DESC")
    List<Alert> findAlertsByPatientAndTimeRange(
        @Param("patient") Patient patient,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find recent alerts for a patient (last N hours).
     *
     * @param patient the patient
     * @param hours the number of hours to look back
     * @return list of recent alerts
     */
    List<Alert> findByPatientAndTriggeredAtAfterOrderByTriggeredAtDesc(
        Patient patient,
        LocalDateTime cutoff
    );

    default List<Alert> findRecentAlertsForPatient(Patient patient, Integer hours) {
        return findByPatientAndTriggeredAtAfterOrderByTriggeredAtDesc(
            patient,
            LocalDateTime.now(ZoneOffset.UTC).minusHours(hours)
        );
    }

    /**
     * Find alerts for a patient by severity.
     *
     * @param patient the patient
     * @param severity the alert severity
     * @return list of alerts with that severity for the patient
     */
    List<Alert> findByPatientAndSeverity(Patient patient, AlertSeverity severity);

    /**
     * Count unacknowledged critical alerts across all patients.
     *
     * @return count of unacknowledged critical alerts
     */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = 'CRITICAL' AND a.acknowledged = false")
    long countUnacknowledgedCriticalAlerts();

    /**
     * Delete all alerts for a patient (used when patient is deleted).
     *
     * @param patient the patient
     * @return number of deleted alerts
     */
    long deleteByPatient(Patient patient);

    @Query("SELECT a FROM Alert a WHERE a.patient IN :patients AND a.acknowledged = false")
    List<Alert> findByPatientInAndAcknowledgedFalse(@Param("patients") List<Patient> patients);

}
