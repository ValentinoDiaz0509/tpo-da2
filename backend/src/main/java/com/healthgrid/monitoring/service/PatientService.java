package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.PatientDTO;
import com.healthgrid.monitoring.exception.ApplicationException;
import com.healthgrid.monitoring.exception.ExceptionEnum;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for patient operations.
 * Handles business logic related to patient management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;

    /**
     * Create a new patient.
     *
     * @param patientDTO the patient data
     * @return the created patient DTO
     */
    public PatientDTO createPatient(PatientDTO patientDTO) {
        log.info("Creating new patient: {}", patientDTO.getName());
        
        Patient patient = Patient.builder()
            .name(patientDTO.getName())
            .room(patientDTO.getRoom())
            .bed(patientDTO.getBed())
            .status(patientDTO.getStatus())
            .build();

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient created successfully with ID: {}", savedPatient.getId());
        
        return convertToDTO(savedPatient);
    }

    /**
     * Get patient by ID.
     *
     * @param id the patient UUID
     * @return the patient DTO
     * @throws RuntimeException if patient not found
     */
    @Transactional(readOnly = true)
    public PatientDTO getPatientById(UUID id) {
        Patient patient = patientRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Patient not found with ID: {}", id);
                return new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, id.toString());
            });
        return convertToDTO(patient);
    }

    /**
     * Get all patients.
     *
     * @return list of patient DTOs
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getAllPatients() {
        log.debug("Fetching all patients");
        return patientRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get all patients with a specific status.
     *
     * @param status the patient status
     * @return list of patient DTOs
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getPatientsByStatus(PatientStatus status) {
        log.debug("Fetching patients with status: {}", status);
        return patientRepository.findByStatus(status)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get all critical patients.
     *
     * @return list of critical patient DTOs
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getCriticalPatients() {
        log.info("Fetching critical patients");
        return patientRepository.findCriticalPatients()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get patients by room number.
     *
     * @param room the room number
     * @return list of patient DTOs
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getPatientsByRoom(String room) {
        log.debug("Fetching patients in room: {}", room);
        return patientRepository.findByRoom(room)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get patients by bed number.
     *
     * @param bed the bed number
     * @return list of patient DTOs
     */
    @Transactional(readOnly = true)
    public List<PatientDTO> getPatientsByBed(String bed) {
        log.debug("Fetching patients in bed: {}", bed);
        return patientRepository.findByBed(bed)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Update a patient.
     *
     * @param id the patient UUID
     * @param patientDTO the updated patient data
     * @return the updated patient DTO
     */
    public PatientDTO updatePatient(UUID id, PatientDTO patientDTO) {
        log.info("Updating patient with ID: {}", id);
        
        Patient patient = patientRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, id.toString()));

        patient.setName(patientDTO.getName());
        patient.setRoom(patientDTO.getRoom());
        patient.setBed(patientDTO.getBed());
        patient.setStatus(patientDTO.getStatus());

        Patient updatedPatient = patientRepository.save(patient);
        log.info("Patient updated successfully with ID: {}", id);
        
        return convertToDTO(updatedPatient);
    }

    /**
     * Delete a patient by ID.
     *
     * @param id the patient UUID
     */
    public void deletePatient(UUID id) {
        log.info("Deleting patient with ID: {}", id);
        
        if (!patientRepository.existsById(id)) {
            throw new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, id.toString());
        }
        
        patientRepository.deleteById(id);
        log.info("Patient deleted successfully with ID: {}", id);
    }

    /**
     * Convert Patient entity to PatientDTO.
     *
     * @param patient the patient entity
     * @return the patient DTO
     */
    private PatientDTO convertToDTO(Patient patient) {
        return PatientDTO.builder()
            .id(patient.getId())
            .name(patient.getName())
            .room(patient.getRoom())
            .bed(patient.getBed())
            .status(patient.getStatus())
            .createdAt(patient.getCreatedAt())
            .updatedAt(patient.getUpdatedAt())
            .build();
    }

}
