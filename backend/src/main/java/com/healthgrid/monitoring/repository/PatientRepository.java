package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Patient entity operations.
 * Provides data access layer for patient records.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByExternalId(String externalId);

    /**
     * Find a patient by their name.
     *
     * @param name the patient name
     * @return list of patients matching the name
     */
    List<Patient> findByName(String name);

    /**
     * Find all patients with a specific status.
     *
     * @param status the patient status
     * @return list of patients with the specified status
     */
    List<Patient> findByStatus(PatientStatus status);

    /**
     * Find all critical patients.
     *
     * @return list of critical patients
     */
    @Query("SELECT p FROM Patient p WHERE p.status = 'CRITICAL'")
    List<Patient> findCriticalPatients();

    /**
     * Find patients by room number.
     *
     * @param room the room number
     * @return list of patients in the specified room
     */
    List<Patient> findByRoom(String room);

    /**
     * Find patients by bed number.
     *
     * @param bed the bed number
     * @return list of patients in the specified bed
     */
    List<Patient> findByBed(String bed);

    /**
     * Find patients by room and bed.
     *
     * @param room the room number
     * @param bed the bed number
     * @return list of patients in the specified room and bed
     */
    List<Patient> findByRoomAndBed(String room, String bed);

    /**
     * Check if a patient with given name exists.
     *
     * @param name the patient name
     * @return true if patient exists
     */
    boolean existsByName(String name);

    /**
     * List active (non-discharged) patients for the monitoring dashboard.
     * A BAJA_MONITOREO sets the patient to INACTIVE, which must drop it from the board.
     */
    @Query("""
        SELECT p FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        """)
    Page<Patient> findAllActive(Pageable pageable);

    @Query("""
        SELECT p FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Patient> findFilteredBySearch(@Param("search") String search, Pageable pageable);

    @Query(value = """
        SELECT p FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        GROUP BY p
        ORDER BY COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) DESC,
            p.name ASC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        """)
    Page<Patient> findAllSortedBySeverity(Pageable pageable);

    @Query(value = """
        SELECT p FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        GROUP BY p
        ORDER BY COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) DESC,
            p.name ASC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Patient> findFilteredSortedBySeverity(@Param("search") String search, Pageable pageable);

    @Query(value = """
        SELECT p FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        GROUP BY p
        ORDER BY COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) ASC,
            p.name ASC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        """)
    Page<Patient> findAllSortedBySeverityAsc(Pageable pageable);

    @Query(value = """
        SELECT p FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        GROUP BY p
        ORDER BY COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) ASC,
            p.name ASC
        """,
        countQuery = """
        SELECT COUNT(p) FROM Patient p
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Patient> findFilteredSortedBySeverityAsc(@Param("search") String search, Pageable pageable);

    @Query("""
        SELECT p.id AS patientId,
        COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) AS severityRank
        FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
        GROUP BY p.id, p.status
        """)
    List<PatientSeverityRank> findAllPatientSeverityRanks();

    @Query("""
        SELECT p.id AS patientId,
        COALESCE(MAX(CASE a.severity
            WHEN com.healthgrid.monitoring.model.AlertSeverity.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.AlertSeverity.WARNING THEN 2
            ELSE NULL END),
            CASE p.status
            WHEN com.healthgrid.monitoring.model.PatientStatus.CRITICAL THEN 3
            WHEN com.healthgrid.monitoring.model.PatientStatus.WARNING THEN 2
            ELSE 1 END) AS severityRank
        FROM Patient p
        LEFT JOIN p.alerts a ON a.acknowledged = false
        WHERE p.status <> com.healthgrid.monitoring.model.PatientStatus.INACTIVE
            AND (:search IS NULL
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.bed) LIKE LOWER(CONCAT('%', :search, '%')))
        GROUP BY p.id, p.status
        """)
    List<PatientSeverityRank> findPatientSeverityRanks(@Param("search") String search);

}
