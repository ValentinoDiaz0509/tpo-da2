package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringAdmissionServiceTest {

    private static final int ALTA_ID = 26;
    private static final int BAJA_ID = 27;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private MonitoringAdmissionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "altaEventId", ALTA_ID);
        ReflectionTestUtils.setField(service, "bajaEventId", BAJA_ID);
    }

    private MonitoreoWebhookRequestDTO payload() {
        return MonitoreoWebhookRequestDTO.builder()
                .pacienteId(123L)
                .camaId(45L)
                .build();
    }

    @Test
    void bajaEventIdSuspendsPatient() {
        Patient patient = Patient.builder()
                .externalId("123")
                .status(PatientStatus.NORMAL)
                .build();
        when(patientRepository.findByExternalId("123")).thenReturn(Optional.of(patient));

        // Event name intentionally lacks the words alta/baja: only the id must decide.
        service.handleEvent(BAJA_ID, "monitoring.requested", payload());

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void altaEventIdReactivatesInactivePatient() {
        Patient patient = Patient.builder()
                .externalId("123")
                .status(PatientStatus.INACTIVE)
                .build();
        when(patientRepository.findByExternalId("123")).thenReturn(Optional.of(patient));

        service.handleEvent(ALTA_ID, "monitoring.requested", payload());

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PatientStatus.NORMAL);
    }

    @Test
    void fallsBackToEventNameWhenIdUnknown() {
        Patient patient = Patient.builder()
                .externalId("123")
                .status(PatientStatus.NORMAL)
                .build();
        when(patientRepository.findByExternalId("123")).thenReturn(Optional.of(patient));

        service.handleEvent(999, "monitoring.baja.requested", payload());

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void ignoresEventWithoutPacienteId() {
        service.handleEvent(BAJA_ID, "monitoring.baja.requested",
                MonitoreoWebhookRequestDTO.builder().build());

        verify(patientRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
