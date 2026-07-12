package com.healthgrid.monitoring.config;

import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.model.Rule;
import com.healthgrid.monitoring.model.RuleOperator;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PatientRepository patientRepository;
    private final RuleRepository ruleRepository;

    @Override
    public void run(String... args) throws Exception {
        if (patientRepository.count() == 0) {
            log.info("Seeding database with mock patients and rules...");

            List<Patient> patients = Arrays.asList(
                Patient.builder().name("Miller, A.").room("Sala General").bed("Cama 01").status(PatientStatus.NORMAL).build(),
                Patient.builder().name("Harrison, E.").room("UTI").bed("Cama 04").status(PatientStatus.CRITICAL).build(),
                Patient.builder().name("Chen, W.").room("Sala General").bed("Cama 07").status(PatientStatus.WARNING).build(),
                Patient.builder().name("Garcia, M.").room("Sala General").bed("Cama 12").status(PatientStatus.NORMAL).build(),
                Patient.builder().name("Smith, J.").room("Sala General").bed("Cama 15").status(PatientStatus.NORMAL).build(),
                Patient.builder().name("Lopez, R.").room("UTI").bed("Cama 18").status(PatientStatus.WARNING).build()
            );
            patientRepository.saveAll(patients);

            if (ruleRepository.count() == 0) {
                List<Rule> rules = Arrays.asList(
                    Rule.builder().description("Taquicardia Critica").metricName("heart_rate").operator(RuleOperator.GREATER_THAN).threshold(120.0f).durationSeconds(60).severity(com.healthgrid.monitoring.model.AlertSeverity.CRITICAL).enabled(true).build(),
                    Rule.builder().description("Desaturacion").metricName("spo2").operator(RuleOperator.LESS_THAN).threshold(90.0f).durationSeconds(60).severity(com.healthgrid.monitoring.model.AlertSeverity.CRITICAL).enabled(true).build()
                );
                ruleRepository.saveAll(rules);
            }

            log.info("Database seeded successfully.");
        }
    }
}
