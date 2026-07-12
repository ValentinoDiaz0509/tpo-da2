package com.healthgrid.monitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthgrid.monitoring.dto.AdmissionEventDTO;
import com.healthgrid.monitoring.dto.AlertaEmergenciaRequestDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {
    
    // TODO(core): reemplazar publicacion directa a SQS por el adapter/event bus definido por Core.
    private final Optional<SqsClient> sqsClient;
    private final ObjectMapper objectMapper;
    private final PatientRepository patientRepository;
    private final RestTemplate restTemplate;
    private final CoreEventPublisher coreEventPublisher;
    
    @Value("${admission-queue-url:http://localhost:4566/000000000000/admission-events-queue}")
    private String admissionQueueUrl;

    @Value("${healthgrid.module6.webhook.alerta-emergencia.url:http://localhost:8086/webhooks/monitoreo/alerta-emergencia}")
    private String module6WebhookAlertaEmergenciaUrl;

    @Value("${healthgrid.module6.webhook.alerta-resuelta.url:http://localhost:8086/webhooks/monitoreo/alerta-resuelta}")
    private String module6WebhookAlertaResueltaUrl;

    @Value("${healthgrid.module6.webhook.token:mock-jwt-token-for-m6}")
    private String module6WebhookToken;

    @Value("${healthgrid.module6.webhook.enabled:true}")
    private boolean module6WebhookEnabled;
    
    /**
     * Publica un evento CRITICAL a Module 6 (Internación) usando REST (Webhook) y Core via SQS.
     */
    public void publishCriticalAlertEvent(Alert alert, Rule rule) {
        try {
            // PASO 1: Recuperar paciente para obtener ubicación
            Patient patient = patientRepository.findById(alert.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));
            
            // PASO 2: Construir AdmissionEventDTO con TODO el contexto requerido (Para Módulo 10 - Core vía SQS)
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
            
            // PASO 3: Serializar a JSON
            String eventPayload = objectMapper.writeValueAsString(event);
            
            // PASO 4: VALIDAR que el JSON contiene los campos requeridos
            validateEventPayload(event);
            
            // PASO 5: Enviar a SQS con Message Group ID (para FIFO order)
            if (sqsClient.isPresent()) {
                SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(admissionQueueUrl)
                    .messageBody(eventPayload)
                    .messageGroupId("admission-events") // FIFO ordering
                    .messageDeduplicationId(
                        generateDeduplicationId(alert, rule)) // Evitar duplicados
                    .build();

                SendMessageResponse response = sqsClient.get().sendMessage(request);

                log.info("✓ CRITICAL ALERT EVENT PUBLISHED TO CORE VIA SQS - Patient: {}, MessageId: {}",
                    alert.getPatient().getId(), response.messageId());
            } else {
                log.info("Skipping SQS publish because aws.sqs.enabled=false");
            }

            // PASO 6: Enviar Webhook REST a Módulo 6 (Internación)
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

                // 6.a: Publish to M10 Core Event Bus
                String m10Payload = objectMapper.writeValueAsString(webhookPayload);
                coreEventPublisher.publishEvent(16, m10Payload);

                // 6.b: Legacy Webhook
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
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize admission event to JSON", e);
            throw new RuntimeException("Event serialization failed", e);
        } catch (SqsException e) {
            log.error("Failed to publish admission event to SQS", e);
            throw new RuntimeException("Event publishing failed", e);
        }
    }
    
    /**
     * Publica un evento de alerta resuelta a Module 6 (Internación).
     */
    public void publishAlertResolvedEvent(Alert alert, String motivoResolucion) {
        if (!module6WebhookEnabled) {
            log.info("Skipping resolved alert webhook because healthgrid.module6.webhook.enabled=false");
            return;
        }

        try {
            Patient patient = patientRepository.findById(alert.getPatient().getId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

            Long pId = patient.getExternalId() != null ? Long.valueOf(patient.getExternalId()) : 0L;
            
            com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO webhookPayload = 
                com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO.builder()
                    .pacienteId(pId)
                    .motivoResolucion(motivoResolucion != null ? motivoResolucion : "Alerta resuelta por personal médico: " + alert.getAcknowledgedBy())
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // M10 Core Event Bus implementation
            String m10Payload = objectMapper.writeValueAsString(webhookPayload);
            coreEventPublisher.publishEvent(17, m10Payload);

            // Legacy Webhook implementation
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + module6WebhookToken);
            headers.set("Content-Type", "application/json");
            
            org.springframework.http.HttpEntity<com.healthgrid.monitoring.dto.AlertaResueltaRequestDTO> httpEntity = 
                new org.springframework.http.HttpEntity<>(webhookPayload, headers);

            ResponseEntity<String> webhookResponse = restTemplate.postForEntity(
                module6WebhookAlertaResueltaUrl, 
                httpEntity, 
                String.class
            );
            
            log.info("✓ WEBHOOK ALERTA RESUELTA SENT TO MODULE 6 - Status: {}, Patient ID: {}", 
                webhookResponse.getStatusCode(), webhookPayload.getPacienteId());
        } catch (Exception ex) {
            log.error("Failed to send alerta resuelta to Module 6", ex);
        }
    }
    
    /**
     * Construye una descripción legible de la regla para Module 6.
     * Ejemplo: "heart_rate > 120.0 for 300 seconds"
     */
    private String buildRuleDescription(Rule rule) {
        if (rule == null) {
            return "Manual Emergency Intervention";
        }
        return String.format("%s %s %.1f for %d seconds",
            rule.getMetricName(),
            rule.getOperator(),
            rule.getThreshold(),
            rule.getDurationSeconds() != null ? rule.getDurationSeconds() : 0);
    }
    
    /**
     * Extrae el valor numérico de la métrica desde el mensaje de alerta.
     * Parsea: "Alert: heart_rate value (135.00) triggered..."
     */
    private Double extractMetricValueFromAlert(Alert alert) {
        // Patrón: "Alert: metric_name value (XXX.XX)"
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
    
    /**
     * Genera un ID único para deduplicación FIFO en SQS.
     */
    private String generateDeduplicationId(Alert alert, Rule rule) {
        String ruleId = rule != null ? String.valueOf(rule.getId()) : "manual";
        return sha256Hex(
            alert.getPatient().getId() + "|" +
            ruleId + "|" + 
            alert.getTriggeredAt().toString()
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
