package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.RuleDTO;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for monitoring rule operations.
 * Handles business logic for rule management and validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RuleService {

    private final RuleRepository ruleRepository;

    /**
     * Create a new monitoring rule.
     *
     * @param ruleDTO the rule data
     * @return the created rule DTO
     */
    public RuleDTO createRule(RuleDTO ruleDTO) {
        log.info("Creating new rule for metric: {}", ruleDTO.getMetricName());
        
        Rule rule = Rule.builder()
            .metricName(ruleDTO.getMetricName())
            .operator(ruleDTO.getOperator())
            .threshold(ruleDTO.getThreshold())
            .durationSeconds(ruleDTO.getDurationSeconds())
            .severity(ruleDTO.getSeverity())
            .description(ruleDTO.getDescription())
            .enabled(ruleDTO.getEnabled() != null ? ruleDTO.getEnabled() : true)
            .build();

        Rule savedRule = ruleRepository.save(rule);
        log.info("Rule created successfully with ID: {}", savedRule.getId());
        
        return convertToDTO(savedRule);
    }

    /**
     * Get rule by ID.
     *
     * @param id the rule UUID
     * @return the rule DTO
     */
    @Transactional(readOnly = true)
    public RuleDTO getRuleById(UUID id) {
        Rule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + id));
        return convertToDTO(rule);
    }

    /**
     * Get all active rules.
     *
     * @return list of active rule DTOs
     */
    @Transactional(readOnly = true)
    public List<RuleDTO> getActiveRules() {
        return ruleRepository.findByEnabledTrue()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get all rules.
     *
     * @return list of rule DTOs
     */
    @Transactional(readOnly = true)
    public List<RuleDTO> getAllRules() {
        return ruleRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get rules by metric name.
     *
     * @param metricName the metric name
     * @return list of rule DTOs for that metric
     */
    @Transactional(readOnly = true)
    public List<RuleDTO> getRulesByMetric(String metricName) {
        return ruleRepository.findByMetricNameAndEnabled(metricName, true)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get all critical rules that are enabled.
     *
     * @return list of critical rule DTOs
     */
    @Transactional(readOnly = true)
    public List<RuleDTO> getCriticalRules() {
        return ruleRepository.findAllCriticalRules()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Update a rule.
     *
     * @param id the rule UUID
     * @param ruleDTO the updated rule data
     * @return the updated rule DTO
     */
    public RuleDTO updateRule(UUID id, RuleDTO ruleDTO) {
        log.info("Updating rule with ID: {}", id);
        
        Rule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + id));

        rule.setMetricName(ruleDTO.getMetricName());
        rule.setOperator(ruleDTO.getOperator());
        rule.setThreshold(ruleDTO.getThreshold());
        rule.setDurationSeconds(ruleDTO.getDurationSeconds());
        rule.setSeverity(ruleDTO.getSeverity());
        rule.setDescription(ruleDTO.getDescription());
        rule.setEnabled(ruleDTO.getEnabled());

        Rule updatedRule = ruleRepository.save(rule);
        log.info("Rule updated successfully with ID: {}", id);
        
        return convertToDTO(updatedRule);
    }

    /**
     * Enable a rule.
     *
     * @param id the rule UUID
     */
    public void enableRule(UUID id) {
        Rule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + id));
        rule.setEnabled(true);
        ruleRepository.save(rule);
        log.info("Rule enabled with ID: {}", id);
    }

    /**
     * Disable a rule.
     *
     * @param id the rule UUID
     */
    public void disableRule(UUID id) {
        Rule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + id));
        rule.setEnabled(false);
        ruleRepository.save(rule);
        log.info("Rule disabled with ID: {}", id);
    }

    /**
     * Delete a rule.
     *
     * @param id the rule UUID
     */
    public void deleteRule(UUID id) {
        if (!ruleRepository.existsById(id)) {
            throw new RuntimeException("Rule not found with ID: " + id);
        }
        ruleRepository.deleteById(id);
        log.info("Rule deleted with ID: {}", id);
    }

    /**
     * Convert Rule entity to DTO.
     *
     * @param rule the rule entity
     * @return the rule DTO
     */
    private RuleDTO convertToDTO(Rule rule) {
        return RuleDTO.builder()
            .id(rule.getId())
            .metricName(rule.getMetricName())
            .operator(rule.getOperator())
            .threshold(rule.getThreshold())
            .durationSeconds(rule.getDurationSeconds())
            .severity(rule.getSeverity())
            .description(rule.getDescription())
            .enabled(rule.getEnabled())
            .createdAt(rule.getCreatedAt())
            .updatedAt(rule.getUpdatedAt())
            .build();
    }

}
