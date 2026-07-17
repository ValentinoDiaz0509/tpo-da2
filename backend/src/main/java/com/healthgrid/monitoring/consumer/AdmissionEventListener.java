package com.healthgrid.monitoring.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthgrid.monitoring.dto.CoreEventEnvelope;
import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.service.MonitoringAdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Listens for patient monitoring events published through the M10 Core event bus.
 *
 * Module 6 (Internación) publishes to the Core (POST /events/log); the Core routes the
 * message to our RabbitMQ queue ({@code monitoring.requests}). We only listen here — queues,
 * events and bindings are provisioned from the Core, per the shared communication tutorial.
 *
 * Wire format: the Core wraps the business payload in a {@link CoreEventEnvelope}, whose
 * {@code payload} field is itself a JSON <b>string</b> — hence the double parse below.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "healthgrid.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionEventListener {

    private final ObjectMapper objectMapper;
    private final MonitoringAdmissionService admissionService;

    @RabbitListener(queues = "${healthgrid.rabbit.monitoring-queue:monitoring.requests}")
    public void onMessage(String rawMessage) {
        CoreEventEnvelope envelope;
        MonitoreoWebhookRequestDTO admission;
        try {
            // 1. Parse the external Core envelope
            envelope = objectMapper.readValue(rawMessage, CoreEventEnvelope.class);
            // 2. Parse the inner payload (arrives as a JSON string)
            admission = objectMapper.readValue(envelope.getPayload(), MonitoreoWebhookRequestDTO.class);
        } catch (Exception e) {
            // Malformed message: don't requeue (it would loop forever) — send it to the DLQ.
            log.error("Discarding unparseable Core event from monitoring queue: {}", rawMessage, e);
            throw new AmqpRejectAndDontRequeueException("Unparseable Core event", e);
        }

        log.info("Received Core event '{}' (id={}) from {} → evento={}, paciente_id={}",
                envelope.getEventTypeName(), envelope.getEventTypeId(), envelope.getPublisherModule(),
                admission.getEvento(), admission.getPacienteId());

        try {
            // 3. Process (idempotent create/reactivate/suspend keyed by external patient id)
            admissionService.handleEvent(admission);
        } catch (Exception e) {
            // Business/infra failure: let the container's retry policy run, then route to the DLQ.
            log.error("Failed to process monitoring event for paciente_id={}", admission.getPacienteId(), e);
            throw e;
        }
        // 4. Successful return => message is ACKed automatically.
    }
}
