package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import java.util.Optional;

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

}
