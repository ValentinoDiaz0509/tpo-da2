package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.MonitoringUpdateDTO;
import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.model.RuleOperator;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.RuleRepository;
import com.healthgrid.monitoring.repository.TelemetryReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RuleEngineService {
    
    private final RuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AlertService alertService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final EventPublisherService eventPublisherService;
    
    private static final int LOOKBACK_MINUTES = 10; // Ventana de análisis
    
    /**
     * Evalúa una lectura de telemetría contra todas las reglas activas.
     * Implementa lógica de ventana de tiempo para detectar violaciones SOSTENIDAS.
     */
    public List<Alert> evaluateReadingAndGenerateAlerts(TelemetryReading reading) {
        List<Alert> generatedAlerts = new ArrayList<>();
        
        // Validar que el paciente existe
        Patient patient = patientRepository.findById(reading.getPatient().getId())
            .orElseThrow(() -> new RuntimeException("Patient not found: " + reading.getPatient().getId()));
        
        // Obtener todas las reglas activas
        List<Rule> activeRules = ruleRepository.findByEnabledTrue();
        
        log.info("Evaluating {} active rules for patient: {}", 
            activeRules.size(), patient.getId());
        
        for (Rule rule : activeRules) {
            // PASO 1: Evaluar la lectura ACTUAL contra la regla
            boolean currentReadingViolates = evaluateMetricAgainstRule(reading, rule);
            
            if (!currentReadingViolates) {
                // La lectura actual es normal, no continuar
                continue;
            }
            
            // PASO 2: SI la lectura actual viola → Verificar VENTANA DE TIEMPO
            // Consultar lecturas históricas de los últimos N minutos
            LocalDateTime lookbackTime = reading.getRecordedAt()
                .minusMinutes(LOOKBACK_MINUTES);
            
            List<TelemetryReading> historicalReadings = 
                telemetryReadingRepository.findReadingsByPatientAndTimeRange(
                    patient,
                    lookbackTime,
                    reading.getRecordedAt()
                );
            
            // PASO 3: Contar cuántas lecturas en la ventana violan la regla
            long violationCount = historicalReadings.stream()
                .filter(r -> evaluateMetricAgainstRule(r, rule))
                .count();
            
            // PASO 4: Calcular duración sostenida de violaciones
            if (historicalReadings.size() > 0) {
                long totalDurationSeconds = calculateSustainedDuration(
                    historicalReadings, 
                    rule
                );
                
                // PASO 5: GENERAR ALERTA solo si la violación es SOSTENIDA
                if (totalDurationSeconds >= rule.getDurationSeconds()) {
                    Alert alert = createAlert(patient, rule, reading, totalDurationSeconds);
                    alertRepository.save(alert);
                    generatedAlerts.add(alert);
                    
                    log.warn("⚠️ ALERT GENERATED [SUSTAINED VIOLATION] - Patient: {}, Rule: {}, Duration: {}s, Threshold: {}s",
                        patient.getId(),
                        rule.getMetricName(),
                        totalDurationSeconds,
                        rule.getDurationSeconds());
                    
                    // Publicar evento a Module 6 (Internación) si es CRITICAL
                    if (AlertSeverity.CRITICAL.equals(rule.getSeverity())) {
                        try {
                            eventPublisherService.publishCriticalAlertEvent(alert, rule);
                            log.info("✓ Critical alert event published to Module 6");
                        } catch (Exception e) {
                            log.error("Failed to publish critical alert event", e);
                        }
                    }
                }
            }
            
            // Enviar actualización en tiempo real por WebSocket
            sendMonitoringUpdate(reading);
        }
        
        return generatedAlerts;
    }
    
    /**
     * Evalúa si una métrica viola la regla (comparación simple).
     * No considera ventana de tiempo.
     */
    private boolean evaluateMetricAgainstRule(TelemetryReading reading, Rule rule) {
        String metricName = rule.getMetricName().toLowerCase();
        Float metricValue = null;
        
        // Extraer el valor de la métrica de la lectura
        switch (metricName) {
            case "heart_rate", "heartrate" -> metricValue = reading.getHeartRate();
            case "spo2", "oxygen_saturation" -> metricValue = reading.getSpO2();
            case "systolic_pressure" -> metricValue = reading.getSystolicPressure();
            case "diastolic_pressure" -> metricValue = reading.getDiastolicPressure();
            case "temperature" -> metricValue = reading.getTemperature();
            default -> {
                log.warn("Unknown metric: {}", metricName);
                return false;
            }
        }
        
        if (metricValue == null) {
            return false;
        }
        
        // Comparar usando el operador de la regla
        return compareValues(metricValue, rule.getThreshold(), rule.getOperator());
    }
    
    /**
     * Compara dos valores según el operador especificado.
     * Soporta: >, >=, <, <=, ==, !=
     */
    private boolean compareValues(float actual, float threshold, RuleOperator operator) {
        final float FLOAT_TOLERANCE = 0.01f; // Tolerancia para comparaciones de flotantes

        return switch (operator) {
            case GREATER_THAN -> actual > threshold;
            case GREATER_THAN_OR_EQUAL -> actual >= threshold;
            case LESS_THAN -> actual < threshold;
            case LESS_THAN_OR_EQUAL -> actual <= threshold;
            case EQUAL -> Math.abs(actual - threshold) < FLOAT_TOLERANCE;
            case NOT_EQUAL -> Math.abs(actual - threshold) >= FLOAT_TOLERANCE;
        };
    }
    
    /**
     * Calcula la duración SOSTENIDA de violaciones consecutivas.
     * Ejemplo: si hay 5 lecturas cada 1 min, y todas violan = 5 minutos sostenidos
     */
    private long calculateSustainedDuration(
            List<TelemetryReading> readings, 
            Rule rule) {
        
        if (readings.isEmpty()) {
            return 0;
        }
        
        // Filtrar solo las que violan la regla
        List<TelemetryReading> violations = readings.stream()
            .filter(r -> evaluateMetricAgainstRule(r, rule))
            .sorted(Comparator.comparing(TelemetryReading::getRecordedAt))
            .toList();
        
        if (violations.isEmpty()) {
            return 0;
        }
        
        // Calcular duración entre la primera y última violación
        LocalDateTime first = violations.get(0).getRecordedAt();
        LocalDateTime last = violations.get(violations.size() - 1).getRecordedAt();
        
        return ChronoUnit.SECONDS.between(first, last);
    }
    
    /**
     * Crea una entidad Alert con toda la información contextual.
     */
    private Alert createAlert(Patient patient, Rule rule, TelemetryReading reading, long sustainedDuration) {
        String metricValue = extractMetricValue(reading, rule.getMetricName());
        
        String message = String.format(
            "Alert: %s value (%s) triggered rule violation. " +
            "Threshold: %.2f, Operator: %s, Sustained Duration: %d seconds",
            rule.getMetricName(),
            metricValue,
            rule.getThreshold(),
            rule.getOperator(),
            sustainedDuration
        );
        
        return Alert.builder()
            .patient(patient)
            .severity(rule.getSeverity())
            .message(message)
            .triggeredAt(LocalDateTime.now())
            .acknowledged(false)
            .build();
    }
    
    /**
     * Extrae el valor de métrica como String para mostrar en el mensaje.
     */
    private String extractMetricValue(TelemetryReading reading, String metricName) {
        return switch (metricName.toLowerCase()) {
            case "heart_rate", "heartrate" -> 
                String.format("%.1f bpm", reading.getHeartRate());
            case "spo2", "oxygen_saturation" -> 
                String.format("%.1f %%", reading.getSpO2());
            case "systolic_pressure" -> 
                String.format("%.0f mmHg", reading.getSystolicPressure());
            case "diastolic_pressure" -> 
                String.format("%.0f mmHg", reading.getDiastolicPressure());
            case "temperature" -> 
                String.format("%.1f°C", reading.getTemperature());
            default -> "unknown";
        };
    }
    
    /**
     * Envía actualización en tiempo real por WebSocket a los clientes suscritos.
     */
    private void sendMonitoringUpdate(TelemetryReading reading) {
        try {
            MonitoringUpdateDTO update = MonitoringUpdateDTO.builder()
                .patientId(reading.getPatient().getId())
                .heartRate(reading.getHeartRate())
                .spO2(reading.getSpO2())
                .systolicPressure(reading.getSystolicPressure())
                .diastolicPressure(reading.getDiastolicPressure())
                .temperature(reading.getTemperature())
                .timestamp(reading.getRecordedAt())
                .build();
            
            simpMessagingTemplate.convertAndSend(
                "/topic/monitoring/" + reading.getPatient().getId(),
                update
            );

            log.debug("✓ WebSocket update sent for patient: {}", reading.getPatient().getId());
        } catch (Exception e) {
            log.warn("Failed to send WebSocket update (non-critical)", e);
        }
    }
}
