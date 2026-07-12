package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.model.Alert;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.model.RuleOperator;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.repository.AlertRepository;
import com.healthgrid.monitoring.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for evaluating health monitoring rules against telemetry readings.
 * Determines if readings trigger alerts based on configured rules and thresholds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HealthRuleEvaluationService {

    private final RuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;

    /**
     * Evaluate all active rules against a telemetry reading.
     * Creates alerts if thresholds are exceeded.
     *
     * @param reading the telemetry reading to evaluate
     * @return list of alerts generated (if any)
     */
    public List<Alert> evaluateReadingAgainstRules(TelemetryReading reading) {
        log.info("Evaluating telemetry reading ID: {} for patient: {} against active rules",
                reading.getId(), reading.getPatient().getId());

        List<Rule> activeRules = ruleRepository.findByEnabledTrue();
        List<Alert> generatedAlerts = new java.util.ArrayList<>();

        for (Rule rule : activeRules) {
            if (evaluateRuleCondition(reading, rule)) {
                Alert alert = generateAlert(reading, rule);
                alert = alertRepository.save(alert);
                generatedAlerts.add(alert);

                log.warn("Alert generated for patient: {}, Rule: {}, Severity: {}",
                        reading.getPatient().getId(), rule.getMetricName(), rule.getSeverity());
            }
        }

        return generatedAlerts;
    }

    /**
     * Evaluate a specific rule condition against a telemetry reading.
     *
     * @param reading the telemetry reading
     * @param rule the rule to evaluate
     * @return true if the condition is met, false otherwise
     */
    private boolean evaluateRuleCondition(TelemetryReading reading, Rule rule) {
        Float metricValue = getMetricValueFromReading(reading, rule.getMetricName());

        if (metricValue == null) {
            log.debug("Metric {} not found in reading for patient: {}",
                    rule.getMetricName(), reading.getPatient().getId());
            return false;
        }

        return compareMetricValue(metricValue, rule.getThreshold(), rule.getOperator());
    }

    /**
     * Get the metric value from a telemetry reading based on metric name.
     *
     * @param reading the telemetry reading
     * @param metricName the metric name
     * @return the metric value or null if not found
     */
    private Float getMetricValueFromReading(TelemetryReading reading, String metricName) {
        return switch (metricName.toLowerCase()) {
            case "heart_rate", "heartrate" -> reading.getHeartRate();
            case "spo2", "oxygen_saturation" -> reading.getSpO2();
            case "systolic_pressure" -> reading.getSystolicPressure();
            case "diastolic_pressure" -> reading.getDiastolicPressure();
            case "temperature" -> reading.getTemperature();
            default -> null;
        };
    }

    /**
     * Compare metric value against threshold using specified operator.
     *
     * @param metricValue the actual metric value
     * @param threshold the threshold value
     * @param operator the comparison operator
     * @return true if condition is met
     */
    private boolean compareMetricValue(Float metricValue, Float threshold, RuleOperator operator) {
        return switch (operator) {
            case GREATER_THAN -> metricValue > threshold;
            case GREATER_THAN_OR_EQUAL -> metricValue >= threshold;
            case LESS_THAN -> metricValue < threshold;
            case LESS_THAN_OR_EQUAL -> metricValue <= threshold;
            case EQUAL -> Math.abs(metricValue - threshold) < 0.01; // Floating point comparison
            case NOT_EQUAL -> Math.abs(metricValue - threshold) >= 0.01;
        };
    }

    /**
     * Generate an alert for a rule violation.
     *
     * @param reading the telemetry reading that triggered the alert
     * @param rule the rule that was violated
     * @return the generated alert
     */
    private Alert generateAlert(TelemetryReading reading, Rule rule) {
        String alertMessage = String.format(
                "Alert: %s value (%.2f) triggered rule violation. Threshold: %.2f, Operator: %s",
                rule.getMetricName(),
                getMetricValueFromReading(reading, rule.getMetricName()),
                rule.getThreshold(),
                rule.getOperator().getSymbol()
        );

        return Alert.builder()
                .patient(reading.getPatient())
                .severity(rule.getSeverity())
                .message(alertMessage)
                .acknowledged(false)
                .triggeredAt(LocalDateTime.now())
                .build();
    }

}
