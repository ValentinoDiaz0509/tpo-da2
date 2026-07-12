package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.model.AlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Rule entity operations.
 * Provides data access layer for monitoring rules.
 */
@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    /**
     * Find all enabled rules.
     *
     * @return list of active rules
     */
    List<Rule> findByEnabledTrue();

    /**
     * Find all disabled rules.
     *
     * @return list of inactive rules
     */
    List<Rule> findByEnabledFalse();

    /**
     * Find rules by metric name.
     *
     * @param metricName the name of the metric to monitor
     * @return list of rules for that metric
     */
    List<Rule> findByMetricName(String metricName);

    /**
     * Find rules by metric name and enabled status.
     *
     * @param metricName the name of the metric to monitor
     * @param enabled whether the rule is enabled
     * @return list of matching rules
     */
    List<Rule> findByMetricNameAndEnabled(String metricName, Boolean enabled);

    /**
     * Find rules by severity level.
     *
     * @param severity the alert severity
     * @return list of rules with that severity
     */
    List<Rule> findBySeverity(AlertSeverity severity);

    /**
     * Find all active rules by severity level.
     *
     * @param severity the alert severity
     * @return list of enabled rules with that severity
     */
    @Query("SELECT r FROM Rule r WHERE r.severity = :severity AND r.enabled = true")
    List<Rule> findByEnabledAndSeverity(@Param("severity") AlertSeverity severity);

    /**
     * Find a rule by metric name and enabled status.
     *
     * @param metricName the metric name
     * @return optional containing the rule if found
     */
    Optional<Rule> findFirstByMetricNameAndEnabledTrueOrderByCreatedAtAsc(String metricName);

    /**
     * Find all CRITICAL severity rules that are enabled.
     *
     * @return list of critical rules
     */
    @Query("SELECT r FROM Rule r WHERE r.severity = 'CRITICAL' AND r.enabled = true")
    List<Rule> findAllCriticalRules();

}
