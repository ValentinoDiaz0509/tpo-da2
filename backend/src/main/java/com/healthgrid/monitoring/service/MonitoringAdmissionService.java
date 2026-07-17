package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
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

    /** Core {@code event_type_id} for a monitoring start (alta). Primary signal on the bus path. */
    @Value("${healthgrid.rabbit.events.alta-id:26}")
    private Integer altaEventId;

    /** Core {@code event_type_id} for a monitoring stop (baja). Primary signal on the bus path. */
    @Value("${healthgrid.rabbit.events.baja-id:27}")
    private Integer bajaEventId;

    private final PatientRepository patientRepository;

    /**
     * Dispatches a monitoring event to {@link #alta(Long, Long)} or {@link #baja(Long, Long)}.
     *
     * <p>Per the Core contract, Module 6 publishes two distinct event types and the numeric
     * <b>{@code event_type_id}</b> is what distinguishes the operation ({@link #altaEventId} vs
     * {@link #bajaEventId}). The event type <i>name</i> is a secondary signal (its exact string is
     * environment-configured), and the payload {@code evento} field is a last-resort fallback for
     * the legacy webhook path. If all are inconclusive we default to alta and log a warning.
     *
     * @param eventTypeId   the Core {@code event_type_id} from the envelope, or {@code null}
     * @param eventTypeName the Core {@code event_type_name} from the envelope, or {@code null}
     * @param request       the parsed monitoring payload
     */
    public void handleEvent(Integer eventTypeId, String eventTypeName, MonitoreoWebhookRequestDTO request) {
        if (request == null || request.getPacienteId() == null) {
            log.warn("Ignoring monitoring event without paciente_id: {}", request);
            return;
        }

        if (isBaja(eventTypeId, eventTypeName, request.getEvento())) {
            baja(request.getPacienteId(), request.getCamaId());
        } else {
            alta(request.getPacienteId(), request.getCamaId());
        }
    }

    /**
     * Resolves whether an event is a discharge (baja). Primary signal is the numeric
     * {@code event_type_id}; falls back to the event type name, then the payload {@code evento} field.
     */
    private boolean isBaja(Integer eventTypeId, String eventTypeName, String eventoField) {
        // 1. Primary signal per the Core contract: the numeric event type id.
        if (eventTypeId != null) {
            if (Objects.equals(eventTypeId, bajaEventId)) return true;
            if (Objects.equals(eventTypeId, altaEventId)) return false;
        }

        // 2. Fallback: event type name substring.
        String type = eventTypeName == null ? "" : eventTypeName.toLowerCase();
        if (type.contains("baja")) return true;
        if (type.contains("alta")) return false;

        // 3. Fallback: legacy single-event / webhook path carries the signal in the payload.
        String evento = eventoField == null ? "" : eventoField.toUpperCase();
        if (evento.contains("BAJA")) return true;
        if (evento.contains("ALTA")) return false;

        log.warn("Could not determine alta/baja from eventTypeId='{}', eventType='{}' nor evento='{}'; defaulting to alta",
                eventTypeId, eventTypeName, eventoField);
        return false;
    }

    /**
     * Starts (or reactivates) monitoring for a patient identified by its Module 6 id.
     *
     * @param pacienteId Module 6 patient id (external id)
     * @param camaId     Module 6 bed id; stored on the patient's {@code bed} when provided (may be null)
     */
    @Transactional
    public void alta(Long pacienteId, Long camaId) {
        String externalId = pacienteId.toString();
        String bed = camaId != null ? camaId.toString() : null;
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            boolean changed = false;
            if (p.getStatus() == PatientStatus.INACTIVE) {
                p.setStatus(PatientStatus.NORMAL); // Reactivate monitoring
                changed = true;
            }
            if (bed != null && !bed.equals(p.getBed())) {
                p.setBed(bed); // Keep the bed in sync with M6
                changed = true;
            }
            if (changed) {
                patientRepository.save(p);
                log.info("Updated monitoring for existing patient {} (cama_id={})", p.getId(), camaId);
            }
        } else {
            // Create a stub patient since Module 6 doesn't send name/room
            Patient newPatient = Patient.builder()
                    .externalId(externalId)
                    .name("Paciente M6-" + externalId)
                    .room("Sala TBD")
                    .bed(bed != null ? bed : "Cama TBD")
                    .status(PatientStatus.NORMAL)
                    .build();
            patientRepository.save(newPatient);
            log.info("Created new patient stub with internal ID: {} (cama_id={})", newPatient.getId(), camaId);
        }
    }

    /**
     * Stops monitoring for a patient identified by its Module 6 id.
     *
     * @param pacienteId Module 6 patient id (external id)
     * @param camaId     Module 6 bed id (informational for baja; may be null)
     */
    @Transactional
    public void baja(Long pacienteId, Long camaId) {
        String externalId = pacienteId.toString();
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            p.setStatus(PatientStatus.INACTIVE);
            patientRepository.save(p);
            log.info("Suspended monitoring for patient: {} (cama_id={})", p.getId(), camaId);
        } else {
            log.warn("Received BAJA_MONITOREO for unknown paciente_id: {} (cama_id={})", pacienteId, camaId);
        }
    }
}
