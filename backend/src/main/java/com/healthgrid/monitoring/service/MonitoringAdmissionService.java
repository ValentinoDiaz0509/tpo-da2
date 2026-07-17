package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles patient monitoring start/stop notifications coming from Module 6 (Internación).
 *
 * The same business logic is reached through two transports:
 *  - the legacy REST webhook ({@code InternacionWebhookController})
 *  - the M10 Core event bus over RabbitMQ ({@code AdmissionEventListener})
 *
 * Both funnel here so the create/reactivate/suspend rules live in one place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringAdmissionService {

    /** Payload {@code evento} values agreed with Module 6. Matching is case-insensitive and substring-based. */
    public static final String EVENTO_ALTA = "ALTA_MONITOREO";
    public static final String EVENTO_BAJA = "BAJA_MONITOREO";

    private final PatientRepository patientRepository;

    /**
     * Dispatches a monitoring event to {@link #alta(Long)} or {@link #baja(Long)} based on its
     * {@code evento} field. Used by the RabbitMQ listener, where a single queue carries both cases.
     */
    public void handleEvent(MonitoreoWebhookRequestDTO request) {
        if (request == null || request.getPacienteId() == null) {
            log.warn("Ignoring monitoring event without paciente_id: {}", request);
            return;
        }

        String evento = request.getEvento() == null ? "" : request.getEvento().toUpperCase();
        if (evento.contains("BAJA")) {
            baja(request.getPacienteId());
        } else {
            // Default to alta: M6's monitoring-start notification is the common case.
            alta(request.getPacienteId());
        }
    }

    /**
     * Starts (or reactivates) monitoring for a patient identified by its Module 6 id.
     */
    @Transactional
    public void alta(Long pacienteId) {
        String externalId = pacienteId.toString();
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            if (p.getStatus() == PatientStatus.INACTIVE) {
                p.setStatus(PatientStatus.NORMAL); // Reactivate monitoring
                patientRepository.save(p);
                log.info("Reactivated monitoring for existing patient: {}", p.getId());
            }
        } else {
            // Create a stub patient since Module 6 doesn't send name/room
            Patient newPatient = Patient.builder()
                    .externalId(externalId)
                    .name("Paciente M6-" + externalId)
                    .room("Sala TBD")
                    .bed("Cama TBD")
                    .status(PatientStatus.NORMAL)
                    .build();
            patientRepository.save(newPatient);
            log.info("Created new patient stub with internal ID: {}", newPatient.getId());
        }
    }

    /**
     * Stops monitoring for a patient identified by its Module 6 id.
     */
    @Transactional
    public void baja(Long pacienteId) {
        String externalId = pacienteId.toString();
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            p.setStatus(PatientStatus.INACTIVE);
            patientRepository.save(p);
            log.info("Suspended monitoring for patient: {}", p.getId());
        } else {
            log.warn("Received BAJA_MONITOREO for unknown paciente_id: {}", pacienteId);
        }
    }
}
