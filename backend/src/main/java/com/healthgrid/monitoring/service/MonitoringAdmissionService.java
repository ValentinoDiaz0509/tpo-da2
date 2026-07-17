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

    /** Legacy payload {@code evento} values (single-event / webhook path). Kept as a fallback signal. */
    public static final String EVENTO_ALTA = "ALTA_MONITOREO";
    public static final String EVENTO_BAJA = "BAJA_MONITOREO";

    private final PatientRepository patientRepository;

    /**
     * Dispatches a monitoring event to {@link #alta(Long)} or {@link #baja(Long)}.
     *
     * <p>Module 6 publishes two distinct event types (one for alta, one for baja), so the
     * <b>Core event type name</b> is the primary signal — any name containing {@code "baja"}
     * is a discharge, {@code "alta"} is an admission. When that is inconclusive (e.g. the legacy
     * webhook, which has no event type) we fall back to the payload {@code evento} field. If both
     * are inconclusive we default to alta and log a warning.
     *
     * @param eventTypeName the Core {@code event_type_name} from the envelope, or {@code null}
     * @param request       the parsed monitoring payload
     */
    public void handleEvent(String eventTypeName, MonitoreoWebhookRequestDTO request) {
        if (request == null || request.getPacienteId() == null) {
            log.warn("Ignoring monitoring event without paciente_id: {}", request);
            return;
        }

        if (isBaja(eventTypeName, request.getEvento())) {
            baja(request.getPacienteId());
        } else {
            alta(request.getPacienteId());
        }
    }

    /** Resolves whether an event is a discharge (baja) from the event type name, falling back to the payload field. */
    private boolean isBaja(String eventTypeName, String eventoField) {
        String type = eventTypeName == null ? "" : eventTypeName.toLowerCase();
        if (type.contains("baja")) return true;
        if (type.contains("alta")) return false;

        // Fallback: legacy single-event / webhook path carries the signal in the payload.
        String evento = eventoField == null ? "" : eventoField.toUpperCase();
        if (evento.contains("BAJA")) return true;
        if (evento.contains("ALTA")) return false;

        log.warn("Could not determine alta/baja from eventType='{}' nor evento='{}'; defaulting to alta",
                eventTypeName, eventoField);
        return false;
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
