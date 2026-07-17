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

    private static final int ALERTA_EMERGENCIA_EVENT_TYPE_ID = 16;
    private static final int ALERTA_RESUELTA_EVENT_TYPE_ID = 17;

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

    /**
     * Publishes an emergency event to M6 through Core, and optionally through the legacy REST webhook.
     */
    public void publishCriticalAlertEvent(Alert alert, Rule rule) {
        try {
            Patient patient = patientRepository.findById(alert.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
            Long pacienteId = getCorePatientId(patient);
            if (pacienteId == null) return;

            String observaciones = rule != null
                ? "Alerta generada por métrica: " + rule.getMetricName() + " valor: " + extractMetricValueFromAlert(alert)
                : alert.getMessage();

            AlertaEmergenciaRequestDTO payload = AlertaEmergenciaRequestDTO.builder()
                .pacienteId(pacienteId)
                .timestamp(alert.getTriggeredAt())
                .observaciones(observaciones)
                .build();

            coreEventPublisher.publishEvent(ALERTA_EMERGENCIA_EVENT_TYPE_ID, objectMapper.writeValueAsString(payload));
            sendLegacyEmergencyWebhook(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Module 6 emergency event", e);
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

            ResponseEntity<String> webhookResponse = restTemplate.postForEntity(
                module6WebhookAlertaEmergenciaUrl,
                httpEntity,
                String.class
            );

            log.info("WEBHOOK ALERT SENT TO MODULE 6 - Status: {}, Patient ID: {}",
                webhookResponse.getStatusCode(), payload.getPacienteId());
        } catch (Exception ex) {
            log.error("Failed to send legacy emergency webhook to Module 6", ex);
        }
    }

    private void sendLegacyResolvedWebhook(AlertaResueltaRequestDTO payload) {
        if (!module6WebhookEnabled) {
            log.info("Skipping Module 6 legacy resolved-alert webhook because healthgrid.module6.webhook.enabled=false");
            return;
        }

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
}
