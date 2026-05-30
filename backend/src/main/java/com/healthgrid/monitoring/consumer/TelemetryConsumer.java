package com.healthgrid.monitoring.consumer;

import com.healthgrid.monitoring.dto.TelemetryMessageDTO;
import com.healthgrid.monitoring.dto.TelemetryReadingDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.service.RuleEngineService;
import com.healthgrid.monitoring.service.TelemetryReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelemetryConsumer {
    
    // TODO(core): adaptar este consumer al contrato de eventos/routing definido por Core.
    private final TelemetryReadingService telemetryReadingService;
    private final RuleEngineService ruleEngineService;
    
    /**
     * Consumidor de mensajes de telemetría desde AWS SQS.
     * Implementa IDEMPOTENCIA usando hash del payload.
     */
    @Bean
    @ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public Consumer<TelemetryMessageDTO> telemetryEventInput() {
        return this::processTelemetryMessage;
    }

    public void processTelemetryMessage(TelemetryMessageDTO telemetryMessage) {
        // TODO(core): revisar nombre de binding, origen de eventos y esquema del mensaje cuando Core intermedie colas.
        try {
            log.info("Received telemetry message from sensor: {} for patient: {}",
                telemetryMessage.getSensorId(),
                telemetryMessage.getPatientId());

            // PASO 1: Generar fingerprint único del mensaje
            String messageFingerprint = generateMessageFingerprint(telemetryMessage);

            // PASO 2: Verificar si ya procesamos este mensaje
            if (isDuplicateMessage(messageFingerprint)) {
                log.warn("⚠️ Duplicate message detected (fingerprint: {}), skipping processing",
                    messageFingerprint);
                return; // Ignorar duplicado
            }

            // PASO 3: Convertir a DTO y guardar lectura (en DynamoDB)
            TelemetryReadingDTO readingDTO = convertToReadingDTO(telemetryMessage);
            TelemetryReadingDTO savedReadingDTO = telemetryReadingService.recordReading(
                telemetryMessage.getPatientId(),
                readingDTO
            );

            // Reconstruir la entidad desde el DTO guardado (DynamoDB usa clave compuesta,
            // no un findById por UUID).
            TelemetryReading savedReading = TelemetryReading.builder()
                .id(savedReadingDTO.getId())
                .patientId(savedReadingDTO.getPatientId())
                .heartRate(savedReadingDTO.getHeartRate())
                .spO2(savedReadingDTO.getSpO2())
                .systolicPressure(savedReadingDTO.getSystolicPressure())
                .diastolicPressure(savedReadingDTO.getDiastolicPressure())
                .temperature(savedReadingDTO.getTemperature())
                .recordedAt(savedReadingDTO.getRecordedAt())
                .build();

            log.info("✓ Telemetry reading saved with ID: {}", savedReading.getId());

            // PASO 4: Evaluar reglas y generar alertas (si es necesario)
            List<Alert> generatedAlerts = ruleEngineService
                .evaluateReadingAndGenerateAlerts(savedReading);

            if (!generatedAlerts.isEmpty()) {
                log.warn("Generated {} alert(s) for patient: {}",
                    generatedAlerts.size(),
                    telemetryMessage.getPatientId());
            }

        } catch (Exception e) {
            log.error("Error processing telemetry message", e);
            // TODO: Enviar a Dead-Letter Queue (DLQ) si es necesario
            throw new RuntimeException("Telemetry processing failed", e);
        }
    }
    
    /**
     * Genera un fingerprint único basado en:
     * - sensor_id
     * - patient_id
     * - timestamp (redondeado a segundos)
     * - valores de métricas (redondeados)
     */
    private String generateMessageFingerprint(TelemetryMessageDTO message) {
        String payload = String.format(
            "%s|%s|%d|%.0f|%.0f|%.0f|%.0f|%.0f",
            message.getSensorId(),
            message.getPatientId(),
            Instant.now().getEpochSecond(),
            Math.round(message.getMetrics().getHeartRate() * 10) / 10.0,
            Math.round(message.getMetrics().getSpO2() * 10) / 10.0,
            Math.round(message.getMetrics().getSystolicPressure() * 10) / 10.0,
            Math.round(message.getMetrics().getDiastolicPressure() * 10) / 10.0,
            Math.round(message.getMetrics().getTemperature() * 10) / 10.0
        );
        
        return sha256Hex(payload);
    }
    
    /**
     * Verifica si un mensaje con este fingerprint ya fue procesado.
     * Usa una tabla de "processed messages" o cache en Redis.
     */
    private boolean isDuplicateMessage(String fingerprint) {
        // TODO(core): implementar idempotencia real con la estrategia acordada con Core.
        // OPCIÓN 1: Usar tabla ProcessedMessage
        // return processedMessageRepository.existsById(fingerprint);
        
        // OPCIÓN 2: Usar Redis (más eficiente)
        // return redisTemplate.hasKey("processed:" + fingerprint);
        
        // OPCIÓN 3: Por ahora, logging simple
        log.debug("Checking if message {} was already processed", fingerprint);
        return false; // TODO(core): Implementar verificación real
    }
    
    /**
     * Convierte TelemetryMessageDTO a TelemetryReadingDTO.
     */
    private TelemetryReadingDTO convertToReadingDTO(TelemetryMessageDTO message) {
        LocalDateTime recordedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

        return TelemetryReadingDTO.builder()
            .heartRate(message.getMetrics().getHeartRate())
            .spO2(message.getMetrics().getSpO2())
            .systolicPressure(message.getMetrics().getSystolicPressure())
            .diastolicPressure(message.getMetrics().getDiastolicPressure())
            .temperature(message.getMetrics().getTemperature())
            .recordedAt(recordedAt)
            .build();
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
