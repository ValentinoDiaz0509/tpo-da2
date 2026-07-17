package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.dto.MonitoreoWebhookResponseDTO;
import com.healthgrid.monitoring.service.MonitoringAdmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/internacion")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks Internación", description = "Endpoints para recibir notificaciones del Módulo 6 (Internación)")
public class InternacionWebhookController {

    private final MonitoringAdmissionService admissionService;

    @PostMapping("/alta-monitoreo")
    @Operation(summary = "Recibir notificación de inicio de monitoreo de un paciente")
    public ResponseEntity<MonitoreoWebhookResponseDTO> altaMonitoreo(@RequestBody MonitoreoWebhookRequestDTO request) {
        log.info("Received ALTA_MONITOREO webhook for patient_id: {}", request.getPacienteId());

        if (request.getPacienteId() == null) {
            return ResponseEntity.badRequest().body(new MonitoreoWebhookResponseDTO("El paciente_id es requerido"));
        }

        admissionService.alta(request.getPacienteId());

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

        admissionService.baja(request.getPacienteId());

        return ResponseEntity.ok(MonitoreoWebhookResponseDTO.builder()
                .mensaje("Monitoreo del paciente " + request.getPacienteId() + " dado de baja")
                .build());
    }
}
