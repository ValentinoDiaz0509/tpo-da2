package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.TelemetryReadingDTO;
import com.healthgrid.monitoring.exception.ApplicationException;
import com.healthgrid.monitoring.exception.ExceptionEnum;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.TelemetryReading;
import com.healthgrid.monitoring.repository.PatientRepository;
import com.healthgrid.monitoring.repository.TelemetryReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for telemetry reading operations.
 * Handles business logic for patient vital signs and metrics data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TelemetryReadingService {

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final PatientRepository patientRepository;

    /**
     * Record a new telemetry reading for a patient.
     *
     * @param patientId the patient ID
     * @param readingDTO the telemetry reading data
     * @return the created reading DTO
     */
    public TelemetryReadingDTO recordReading(UUID patientId, TelemetryReadingDTO readingDTO) {
        log.info("Recording telemetry reading for patient ID: {}", patientId);
        
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        TelemetryReading reading = TelemetryReading.builder()
            .patient(patient)
            .heartRate(readingDTO.getHeartRate())
            .spO2(readingDTO.getSpO2())
            .systolicPressure(readingDTO.getSystolicPressure())
            .diastolicPressure(readingDTO.getDiastolicPressure())
            .temperature(readingDTO.getTemperature())
            .recordedAt(readingDTO.getRecordedAt() != null ? readingDTO.getRecordedAt() : LocalDateTime.now())
            .build();

        TelemetryReading savedReading = telemetryReadingRepository.save(reading);
        log.info("Telemetry reading recorded successfully for patient ID: {}", patientId);
        
        return convertToDTO(savedReading);
    }

    /**
     * Get the latest reading for a patient.
     *
     * @param patientId the patient ID
     * @return the latest reading DTO
     */
    @Transactional(readOnly = true)
    public TelemetryReadingDTO getLatestReading(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        TelemetryReading reading = telemetryReadingRepository.findLatestReadingForPatient(patient);
        if (reading == null) {
            throw new ApplicationException(ExceptionEnum.TELEMETRY_READING_NOT_FOUND, patientId.toString());
        }
        
        return convertToDTO(reading);
    }

    /**
     * Get all readings for a patient.
     *
     * @param patientId the patient ID
     * @return list of reading DTOs
     */
    @Transactional(readOnly = true)
    public List<TelemetryReadingDTO> getPatientReadings(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return telemetryReadingRepository.findLatestReadingsByPatient(patient)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get readings for a patient within a time range.
     *
     * @param patientId the patient ID
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @return list of reading DTOs
     */
    @Transactional(readOnly = true)
    public List<TelemetryReadingDTO> getReadingsByTimeRange(UUID patientId, LocalDateTime startTime, LocalDateTime endTime) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return telemetryReadingRepository.findReadingsByPatientAndTimeRange(patient, startTime, endTime)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get readings where heart rate is abnormally high.
     *
     * @param patientId the patient ID
     * @param threshold the heart rate threshold
     * @return list of readings with high heart rate
     */
    @Transactional(readOnly = true)
    public List<TelemetryReadingDTO> getHighHeartRateReadings(UUID patientId, Float threshold) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return telemetryReadingRepository.findByPatientAndHighHeartRate(patient, threshold)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get readings where SpO2 is abnormally low.
     *
     * @param patientId the patient ID
     * @param threshold the SpO2 threshold
     * @return list of readings with low SpO2
     */
    @Transactional(readOnly = true)
    public List<TelemetryReadingDTO> getLowSpO2Readings(UUID patientId, Float threshold) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ApplicationException(ExceptionEnum.PATIENT_NOT_FOUND, patientId.toString()));

        return telemetryReadingRepository.findByPatientAndLowSpO2(patient, threshold)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Convert TelemetryReading entity to DTO.
     *
     * @param reading the reading entity
     * @return the reading DTO
     */
    private TelemetryReadingDTO convertToDTO(TelemetryReading reading) {
        return TelemetryReadingDTO.builder()
            .id(reading.getId())
            .patientId(reading.getPatient().getId())
            .heartRate(reading.getHeartRate())
            .spO2(reading.getSpO2())
            .systolicPressure(reading.getSystolicPressure())
            .diastolicPressure(reading.getDiastolicPressure())
            .temperature(reading.getTemperature())
            .recordedAt(reading.getRecordedAt())
            .build();
    }

}
