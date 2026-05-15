package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.PatientDTO;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for patient management endpoints.
 * Provides CRUD operations for patient records.
 */
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
@Tag(name = "Patient Management", description = "Endpoints for managing patient records")
public class PatientController {

    private final PatientService patientService;

    /**
     * Create a new patient.
     *
     * @param patientDTO the patient data
     * @return the created patient DTO with HTTP 201 status
     */
    @PostMapping
    @Operation(summary = "Create a new patient", description = "Create a new patient record in the system")
    public ResponseEntity<PatientDTO> createPatient(@Valid @RequestBody PatientDTO patientDTO) {
        PatientDTO createdPatient = patientService.createPatient(patientDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPatient);
    }

    /**
     * Get patient by ID.
     *
     * @param id the patient UUID
     * @return the patient DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID", description = "Retrieve a patient record by its unique identifier")
    public ResponseEntity<PatientDTO> getPatientById(@PathVariable UUID id) {
        PatientDTO patient = patientService.getPatientById(id);
        return ResponseEntity.ok(patient);
    }

    /**
     * Get all patients.
     *
     * @return list of patient DTOs
     */
    @GetMapping
    @Operation(summary = "Get all patients", description = "Retrieve all patient records in the system")
    public ResponseEntity<List<PatientDTO>> getAllPatients() {
        List<PatientDTO> patients = patientService.getAllPatients();
        return ResponseEntity.ok(patients);
    }

    /**
     * Get patients by status.
     *
     * @param status the patient status
     * @return list of patient DTOs with the specified status
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get patients by status", description = "Retrieve all patients with a specific status")
    public ResponseEntity<List<PatientDTO>> getPatientsByStatus(@PathVariable PatientStatus status) {
        List<PatientDTO> patients = patientService.getPatientsByStatus(status);
        return ResponseEntity.ok(patients);
    }

    /**
     * Get critical patients.
     *
     * @return list of critical patient DTOs
     */
    @GetMapping("/critical")
    @Operation(summary = "Get critical patients", description = "Retrieve all patients with critical status")
    public ResponseEntity<List<PatientDTO>> getCriticalPatients() {
        List<PatientDTO> patients = patientService.getCriticalPatients();
        return ResponseEntity.ok(patients);
    }

    /**
     * Get patients by room number.
     *
     * @param room the room number
     * @return list of patient DTOs in the room
     */
    @GetMapping("/room/{room}")
    @Operation(summary = "Get patients by room", description = "Retrieve all patients in a specific room")
    public ResponseEntity<List<PatientDTO>> getPatientsByRoom(@PathVariable String room) {
        List<PatientDTO> patients = patientService.getPatientsByRoom(room);
        return ResponseEntity.ok(patients);
    }

    /**
     * Get patients by bed number.
     *
     * @param bed the bed number
     * @return list of patient DTOs in the bed
     */
    @GetMapping("/bed/{bed}")
    @Operation(summary = "Get patients by bed", description = "Retrieve all patients in a specific bed")
    public ResponseEntity<List<PatientDTO>> getPatientsByBed(@PathVariable String bed) {
        List<PatientDTO> patients = patientService.getPatientsByBed(bed);
        return ResponseEntity.ok(patients);
    }

    /**
     * Update a patient.
     *
     * @param id the patient UUID
     * @param patientDTO the updated patient data
     * @return the updated patient DTO
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a patient", description = "Update an existing patient record")
    public ResponseEntity<PatientDTO> updatePatient(
        @PathVariable UUID id,
        @Valid @RequestBody PatientDTO patientDTO) {
        PatientDTO updatedPatient = patientService.updatePatient(id, patientDTO);
        return ResponseEntity.ok(updatedPatient);
    }

    /**
     * Delete a patient.
     *
     * @param id the patient UUID
     * @return HTTP 204 No Content status
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a patient", description = "Delete a patient record from the system")
    public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {
        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }

}
