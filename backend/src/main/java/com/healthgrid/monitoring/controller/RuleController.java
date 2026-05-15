package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.RuleDTO;
import com.healthgrid.monitoring.model.AlertSeverity;
import com.healthgrid.monitoring.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for monitoring rule operations.
 * Provides endpoints for managing and querying monitoring rules.
 */
@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rules", description = "Monitoring rule management")
public class RuleController {

    private final RuleService ruleService;

    /**
     * Create a new monitoring rule.
     *
     * @param ruleDTO the rule data
     * @return created rule response
     */
    @PostMapping
    @Operation(summary = "Create rule", description = "Create a new monitoring rule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Rule created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid rule data")
    })
    public ResponseEntity<RuleDTO> createRule(@Valid @RequestBody RuleDTO ruleDTO) {
        log.info("Creating rule for metric: {}", ruleDTO.getMetricName());
        RuleDTO created = ruleService.createRule(ruleDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get rule by ID.
     *
     * @param id the rule UUID
     * @return rule data
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get rule", description = "Retrieve a specific rule by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rule retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class))),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<RuleDTO> getRuleById(
            @Parameter(description = "Rule UUID", required = true)
            @PathVariable UUID id) {
        log.info("Fetching rule: {}", id);
        RuleDTO rule = ruleService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }

    /**
     * Get all active rules.
     *
     * @return list of active rules
     */
    @GetMapping("/active")
    @Operation(summary = "Get active rules", description = "Retrieve all currently active monitoring rules")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rules retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class)))
    })
    public ResponseEntity<List<RuleDTO>> getActiveRules() {
        log.info("Fetching all active rules");
        List<RuleDTO> rules = ruleService.getActiveRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * Get all rules.
     *
     * @return list of all rules
     */
    @GetMapping
    @Operation(summary = "Get all rules", description = "Retrieve all monitoring rules (active and inactive)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rules retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class)))
    })
    public ResponseEntity<List<RuleDTO>> getAllRules() {
        log.info("Fetching all rules");
        List<RuleDTO> rules = ruleService.getAllRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * Get rules by metric name.
     *
     * @param metricName the metric name
     * @return list of rules for that metric
     */
    @GetMapping("/metric/{metricName}")
    @Operation(summary = "Get rules by metric", description = "Retrieve all rules for a specific metric")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rules retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class)))
    })
    public ResponseEntity<List<RuleDTO>> getRulesByMetric(
            @Parameter(description = "Metric name", required = true)
            @PathVariable String metricName) {
        log.info("Fetching rules for metric: {}", metricName);
        List<RuleDTO> rules = ruleService.getRulesByMetric(metricName);
        return ResponseEntity.ok(rules);
    }

    /**
     * Get all critical rules.
     *
     * @return list of critical rules
     */
    @GetMapping("/critical")
    @Operation(summary = "Get critical rules", description = "Retrieve all rules with CRITICAL severity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rules retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class)))
    })
    public ResponseEntity<List<RuleDTO>> getCriticalRules() {
        log.info("Fetching critical rules");
        List<RuleDTO> rules = ruleService.getCriticalRules();
        return ResponseEntity.ok(rules);
    }

    /**
     * Update a rule.
     *
     * @param id the rule UUID
     * @param ruleDTO the updated rule data
     * @return updated rule response
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update rule", description = "Update an existing monitoring rule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rule updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RuleDTO.class))),
        @ApiResponse(responseCode = "404", description = "Rule not found"),
        @ApiResponse(responseCode = "400", description = "Invalid rule data")
    })
    public ResponseEntity<RuleDTO> updateRule(
            @Parameter(description = "Rule UUID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody RuleDTO ruleDTO) {
        log.info("Updating rule: {}", id);
        RuleDTO updated = ruleService.updateRule(id, ruleDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * Enable a rule.
     *
     * @param id the rule UUID
     * @return no content response
     */
    @PatchMapping("/{id}/enable")
    @Operation(summary = "Enable rule", description = "Activate a monitoring rule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Rule enabled successfully"),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<Void> enableRule(
            @Parameter(description = "Rule UUID", required = true)
            @PathVariable UUID id) {
        log.info("Enabling rule: {}", id);
        ruleService.enableRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Disable a rule.
     *
     * @param id the rule UUID
     * @return no content response
     */
    @PatchMapping("/{id}/disable")
    @Operation(summary = "Disable rule", description = "Deactivate a monitoring rule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Rule disabled successfully"),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<Void> disableRule(
            @Parameter(description = "Rule UUID", required = true)
            @PathVariable UUID id) {
        log.info("Disabling rule: {}", id);
        ruleService.disableRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a rule.
     *
     * @param id the rule UUID
     * @return no content response
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete rule", description = "Remove a monitoring rule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Rule deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    public ResponseEntity<Void> deleteRule(
            @Parameter(description = "Rule UUID", required = true)
            @PathVariable UUID id) {
        log.info("Deleting rule: {}", id);
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

}
