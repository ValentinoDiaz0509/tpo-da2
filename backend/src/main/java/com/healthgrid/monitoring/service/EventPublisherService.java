package com.healthgrid.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthgrid.monitoring.dto.AlertaEmergenciaRequestDTO;
import com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final ObjectMapper objectMapper;
    private final PatientRepository patientRepository;
    private final RestTemplate restTemplate;
    private final CoreEventPublisher coreEventPublisher;

    @Value("${healthgrid.module6.webhook.alerta-emergencia.url:http://localhost:8086/webhooks/monitoreo/alerta-emergencia}")
    private String module6WebhookAlertaEmergenciaUrl;

    @Value("${healthgrid.module6.webhook.alerta-resuelta.url:http://localhost:8086/webhooks/monitoreo/alerta-resuelta}")
    private String module6WebhookAlertaResueltaUrl;

    @Value("${healthgrid.module6.webhook.token:mock-jwt-token-for-m6}")
    private String module6WebhookToken;

    @Value("${healthgrid.module6.webhook.enabled:true}")
    private boolean module6WebhookEnabled;

    // event_type_id en el Core (M10) — internacion.alerta-emergencia.detectada / internacion.alerta-resuelta.notificada
    @Value("${healthgrid.module10.core.events.alerta-emergencia-id:16}")
    private int alertaEmergenciaEventId;

    @Value("${healthgrid.module10.core.events.alerta-resuelta-id:17}")
    private int alertaResueltaEventId;
    
    /**
     * Publica un evento CRITICAL a Module 6 (Internación): al bus de eventos del Core (M10)
     * y, como fallback legacy, por webhook REST directo.
     */
    public void publishCriticalAlertEvent(Alert alert, Rule rule) {
        // PASO 1: Recuperar paciente para obtener ubicación
        Patient patient = patientRepository.findById(alert.getPatient().getId())
            .orElseThrow(() -> new RuntimeException("Patient not found"));

        // PASO 2: Construir AdmissionEventDTO con TODO el contexto requerido y validar
        AdmissionEventDTO event = AdmissionEventDTO.builder()
            .patientId(alert.getPatient().getId())
            .alertSeverity(alert.getSeverity().name())
            .location(patient.getRoom() + "-" + patient.getBed()) // REQUERIDO
            .triggeredRule(buildRuleDescription(rule)) // REQUERIDO
            .metricName(rule != null ? rule.getMetricName() : "Manual")
            .metricValue(extractMetricValueFromAlert(alert))
            .timestamp(alert.getTriggeredAt())
            .message(alert.getMessage())
            .sensorId(extractSensorIdFromAlert(alert))
            .acknowledgmentRequired(true)
            .priorityLevel("RED") // Código Rojo
            .build();

        // PASO 3: VALIDAR que el evento contiene los campos requeridos
        validateEventPayload(event);

        // PASO 4: Publicar a Module 6 (Internación)
        if (!module6WebhookEnabled) {
            log.info("Skipping Module 6 webhook because healthgrid.module6.webhook.enabled=false");
            return;
        }

        try {
            Long pId = patient.getExternalId() != null ? Long.valueOf(patient.getExternalId()) : 0L;

            String obs = rule != null
                ? "Alerta generada por métrica: " + rule.getMetricName() + " valor: " + extractMetricValueFromAlert(alert)
                : alert.getMessage();

            AlertaEmergenciaRequestDTO webhookPayload = AlertaEmergenciaRequestDTO.builder()
                .pacienteId(pId)
                .timestamp(alert.getTriggeredAt())
                .observaciones(obs)
                .build();

            // 4.a: Publish to M10 Core Event Bus
            String m10Payload = objectMapper.writeValueAsString(webhookPayload);
            coreEventPublisher.publishEvent(alertaEmergenciaEventId, m10Payload);

            // 4.b: Legacy Webhook
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + module6WebhookToken);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<AlertaEmergenciaRequestDTO> httpEntity = new org.springframework.http.HttpEntity<>(webhookPayload, headers);

            ResponseEntity<String> webhookResponse = restTemplate.postForEntity(
                module6WebhookAlertaEmergenciaUrl,
                httpEntity,
                String.class
            );

            log.info("✓ WEBHOOK ALERT SENT TO MODULE 6 - Status: {}, Patient ID: {}",
                webhookResponse.getStatusCode(), webhookPayload.getPacienteId());
        } catch (Exception ex) {
            log.error("Failed to send alert to Module 6", ex);
        }
    }

    /**
     * Publishes a resolved-alert event to M6 through Core, and optionally through the legacy REST webhook.
     */
    public void publishAlertResolvedEvent(Alert alert, String motivoResolucion) {
        try {
            Patient patient = patientRepository.findById(alert.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
            Long pacienteId = getCorePatientId(patient);
            if (pacienteId == null) return;

            AlertaResueltaRequestDTO payload = AlertaResueltaRequestDTO.builder()
                .pacienteId(pacienteId)
                .motivoResolucion(motivoResolucion != null
                    ? motivoResolucion
                    : "Alerta resuelta por personal médico: " + alert.getAcknowledgedBy())
                .timestamp(java.time.LocalDateTime.now())
                .build();

            coreEventPublisher.publishEvent(ALERTA_RESUELTA_EVENT_TYPE_ID, objectMapper.writeValueAsString(payload));
            sendLegacyResolvedWebhook(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Module 6 resolved-alert event", e);
        }
    }

    private void sendLegacyEmergencyWebhook(AlertaEmergenciaRequestDTO payload) {
        if (!module6WebhookEnabled) {
            log.info("Skipping Module 6 legacy emergency webhook because healthgrid.module6.webhook.enabled=false");
            return;
        }

        try {
            org.springframework.http.HttpHeaders headers = buildLegacyHeaders();
            org.springframework.http.HttpEntity<AlertaEmergenciaRequestDTO> httpEntity =
                new org.springframework.http.HttpEntity<>(payload, headers);

            Long pId = patient.getExternalId() != null ? Long.valueOf(patient.getExternalId()) : 0L;
            
            com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO webhookPayload = 
                com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO.builder()
                    .pacienteId(pId)
                    .motivoResolucion(motivoResolucion != null ? motivoResolucion : "Alerta resuelta por personal médico: " + alert.getAcknowledgedBy())
                    .timestamp(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                    .build();

            // M10 Core Event Bus implementation
            String m10Payload = objectMapper.writeValueAsString(webhookPayload);
            coreEventPublisher.publishEvent(alertaResueltaEventId, m10Payload);

        try {
            org.springframework.http.HttpHeaders headers = buildLegacyHeaders();
            org.springframework.http.HttpEntity<AlertaResueltaRequestDTO> httpEntity =
                new org.springframework.http.HttpEntity<>(payload, headers);

            ResponseEntity<String> webhookResponse = restTemplate.postForEntity(
                module6WebhookAlertaResueltaUrl,
                httpEntity,
                String.class
            );

            log.info("WEBHOOK ALERTA RESUELTA SENT TO MODULE 6 - Status: {}, Patient ID: {}",
                webhookResponse.getStatusCode(), payload.getPacienteId());
        } catch (Exception ex) {
            log.error("Failed to send legacy resolved-alert webhook to Module 6", ex);
        }
    }

    private org.springframework.http.HttpHeaders buildLegacyHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + module6WebhookToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private Long getCorePatientId(Patient patient) {
        String externalId = patient.getExternalId();
        if (externalId == null || externalId.isBlank()) {
            log.warn("Skipping Module 6 event for patient {} because externalId/paciente_id is missing", patient.getId());
            return null;
        }

        try {
            return Long.valueOf(externalId);
        } catch (NumberFormatException e) {
            log.warn("Skipping Module 6 event for patient {} because externalId '{}' is not numeric", patient.getId(), externalId);
            return null;
        }
    }

    /**
     * Extracts the metric value from alert messages like: "Alert: heart_rate value (135.00) triggered...".
     */
    private Double extractMetricValueFromAlert(Alert alert) {
        Pattern pattern = Pattern.compile("value \\(([\\d.]+)\\)");
        Matcher matcher = pattern.matcher(alert.getMessage());

        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }
    
    /**
     * Extrae el sensor ID del contexto del paciente o metadata.
     */
    private String extractSensorIdFromAlert(Alert alert) {
        // TODO(core): recuperar sensor_id real desde el contrato/evento original administrado por Core.
        return "SENSOR-UNKNOWN";
    }
    
    /**
     * Valida que el evento contiene TODOS los campos requeridos por Module 6.
     */
    private void validateEventPayload(AdmissionEventDTO event) {
        List<String> errors = new ArrayList<>();
        
        if (event.getPatientId() == null) {
            errors.add("patient_id is required");
        }
        if (StringUtils.isBlank(event.getAlertSeverity())) {
            errors.add("alert_severity is required");
        }
        if (StringUtils.isBlank(event.getLocation())) {
            errors.add("location is required (missing patient room/bed)");
        }
        if (StringUtils.isBlank(event.getTriggeredRule())) {
            errors.add("triggered_rule is required");
        }
        if (event.getTimestamp() == null) {
            errors.add("timestamp is required");
        }
        
        if (!errors.isEmpty()) {
            log.error("❌ INVALID ADMISSION EVENT - Errors: {}", errors);
            throw new IllegalArgumentException("Event validation failed: " + String.join("; ", errors));
        }
        
        log.debug("✓ Admission event payload validation passed");
    }
}
