package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.dto.MonitoreoWebhookResponseDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/webhooks/internacion")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks Internación", description = "Endpoints para recibir notificaciones del Módulo 6 (Internación)")
public class InternacionWebhookController {

    private final PatientRepository patientRepository;

    @PostMapping("/alta-monitoreo")
    @Operation(summary = "Recibir notificación de inicio de monitoreo de un paciente")
    public ResponseEntity<MonitoreoWebhookResponseDTO> altaMonitoreo(@RequestBody MonitoreoWebhookRequestDTO request) {
        log.info("Received ALTA_MONITOREO webhook for patient_id: {}", request.getPacienteId());

        if (request.getPacienteId() == null) {
            return ResponseEntity.badRequest().body(new MonitoreoWebhookResponseDTO("El paciente_id es requerido"));
        }

        String externalId = request.getPacienteId().toString();
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            if (p.getStatus() == PatientStatus.INACTIVE) {
                p.setStatus(PatientStatus.NORMAL); // Reactive monitoring
                patientRepository.save(p);
                log.info("Reactivated existing patient: {}", p.getId());
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

        return ResponseEntity.ok(MonitoreoWebhookResponseDTO.builder()
                .mensaje("Monitoreo del paciente " + request.getPacienteId() + " iniciado")
                .build());
    }

    @PostMapping("/baja-monitoreo")
    @Operation(summary = "Recibir notificación de fin de monitoreo de un paciente")
    public ResponseEntity<MonitoreoWebhookResponseDTO> bajaMonitoreo(@RequestBody MonitoreoWebhookRequestDTO request) {
        log.info("Received BAJA_MONITOREO webhook for patient_id: {}", request.getPacienteId());

        if (request.getPacienteId() == null) {
            return ResponseEntity.badRequest().body(new MonitoreoWebhookResponseDTO("El paciente_id es requerido"));
        }

        String externalId = request.getPacienteId().toString();
        Optional<Patient> existingPatient = patientRepository.findByExternalId(externalId);

        if (existingPatient.isPresent()) {
            Patient p = existingPatient.get();
            p.setStatus(PatientStatus.INACTIVE);
            patientRepository.save(p);
            log.info("Suspended monitoring for patient: {}", p.getId());
        } else {
            log.warn("Received BAJA_MONITOREO for unknown patient_id: {}", request.getPacienteId());
        }

        return ResponseEntity.ok(MonitoreoWebhookResponseDTO.builder()
                .mensaje("Monitoreo del paciente " + request.getPacienteId() + " dado de baja")
                .build());
    }
}
