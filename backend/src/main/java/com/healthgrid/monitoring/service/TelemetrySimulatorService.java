package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.TelemetryMessageDTO;
import com.healthgrid.monitoring.dto.TelemetryMetricsDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.consumer.TelemetryConsumer;
import com.healthgrid.monitoring.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetrySimulatorService {

    private final PatientRepository patientRepository;
    private final TelemetryConsumer telemetryConsumer;

    private final Random random = new Random();

    // Variable to control simulated spikes
    private boolean spikeMode = false;

    @Scheduled(fixedRateString = "${simulator.rate:3000}")
    public void generateMockTelemetry() {
        List<Patient> patients = patientRepository.findAll();
        if (patients.isEmpty()) return;

        log.info("Generating mock telemetry for {} patients", patients.size());

        for (Patient p : patients) {
            if (p.getStatus() == com.healthgrid.monitoring.model.PatientStatus.INACTIVE) {
                continue;
            }

            float hrBase = "UTI".equals(p.getRoom()) ? 90f : 70f;
            float spo2Base = "UTI".equals(p.getRoom()) ? 94f : 98f;

            // Optional: simulate critical spike for a specific patient to trigger alerts
            if (spikeMode && p.getName().contains("Harrison")) {
                hrBase = 135f; // Critical
                spo2Base = 88f; // Critical
            }

            TelemetryMetricsDTO metrics = TelemetryMetricsDTO.builder()
                .heartRate(hrBase + (random.nextFloat() * 10 - 5))
                .spO2(Math.min(100f, spo2Base + (random.nextFloat() * 4 - 2)))
                .systolicPressure(120f + (random.nextFloat() * 10 - 5))
                .diastolicPressure(80f + (random.nextFloat() * 10 - 5))
                .temperature(36.5f + (random.nextFloat() * 1 - 0.5f))
                .build();

            TelemetryMessageDTO msg = TelemetryMessageDTO.builder()
                .sensorId("SIMULATOR-" + p.getBed())
                .patientId(p.getId())
                .metrics(metrics)
                .build();

            // Directly call the consumer to process it, bypassing real SQS
            telemetryConsumer.processTelemetryMessage(msg);
        }
    }

    public void toggleSpikeMode(boolean enabled) {
        this.spikeMode = enabled;
        log.info("Simulator Spike Mode set to: {}", enabled);
    }
}
