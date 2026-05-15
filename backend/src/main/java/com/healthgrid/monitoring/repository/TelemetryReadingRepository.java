package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for TelemetryReading entity operations.
 * Provides data access layer for patient telemetry/vitals data.
 */
@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {

    /**
     * Find all telemetry readings for a specific patient.
     *
     * @param patient the patient
     * @return list of telemetry readings for the patient
     */
    List<TelemetryReading> findByPatient(Patient patient);

    /**
     * Find all telemetry readings for a specific patient, ordered by recorded time (descending).
     *
     * @param patient the patient
     * @return list of telemetry readings ordered by most recent first
     */
    @Query("SELECT t FROM TelemetryReading t WHERE t.patient = :patient ORDER BY t.recordedAt DESC")
    List<TelemetryReading> findLatestReadingsByPatient(@Param("patient") Patient patient);

    /**
     * Find telemetry readings for a patient within a time range.
     *
     * @param patient the patient
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @return list of telemetry readings within the time range
     */
    @Query("SELECT t FROM TelemetryReading t WHERE t.patient = :patient AND t.recordedAt BETWEEN :startTime AND :endTime ORDER BY t.recordedAt DESC")
    List<TelemetryReading> findReadingsByPatientAndTimeRange(
        @Param("patient") Patient patient,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find the latest telemetry reading for a patient.
     *
     * @param patient the patient
     * @return the most recent telemetry reading
     */
    TelemetryReading findFirstByPatientOrderByRecordedAtDesc(Patient patient);

    default TelemetryReading findLatestReadingForPatient(Patient patient) {
        return findFirstByPatientOrderByRecordedAtDesc(patient);
    }

    /**
     * Find readings where heart rate exceeds a threshold.
     *
     * @param patient the patient
     * @param threshold the heart rate threshold
     * @return list of readings with high heart rate
     */
    @Query("SELECT t FROM TelemetryReading t WHERE t.patient = :patient AND t.heartRate > :threshold ORDER BY t.recordedAt DESC")
    List<TelemetryReading> findByPatientAndHighHeartRate(
        @Param("patient") Patient patient,
        @Param("threshold") Float threshold
    );

    /**
     * Find readings where SpO2 is below a threshold.
     *
     * @param patient the patient
     * @param threshold the SpO2 threshold
     * @return list of readings with low SpO2
     */
    @Query("SELECT t FROM TelemetryReading t WHERE t.patient = :patient AND t.spO2 < :threshold ORDER BY t.recordedAt DESC")
    List<TelemetryReading> findByPatientAndLowSpO2(
        @Param("patient") Patient patient,
        @Param("threshold") Float threshold
    );

    /**
     * Delete all readings for a patient (used when patient is deleted).
     *
     * @param patient the patient
     * @return number of deleted readings
     */
    long deleteByPatient(Patient patient);

}
