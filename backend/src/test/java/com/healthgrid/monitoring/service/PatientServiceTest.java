package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.PatientDTO;
import com.healthgrid.monitoring.model.Patient;
import com.healthgrid.monitoring.model.PatientStatus;
import com.healthgrid.monitoring.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientService patientService;

    private PatientDTO testPatientDTO;
    private Patient testPatient;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();

        testPatientDTO = PatientDTO.builder()
            .name("Juan Perez")
            .room("101")
            .bed("A")
            .status(PatientStatus.NORMAL)
            .build();

        testPatient = Patient.builder()
            .id(patientId)
            .name("Juan Perez")
            .room("101")
            .bed("A")
            .status(PatientStatus.NORMAL)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void testCreatePatient() {
        when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);

        PatientDTO result = patientService.createPatient(testPatientDTO);

        assertNotNull(result);
        assertEquals("Juan Perez", result.getName());
        assertEquals("101", result.getRoom());
        verify(patientRepository, times(1)).save(any(Patient.class));
    }

    @Test
    void testGetPatientById() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(testPatient));

        PatientDTO result = patientService.getPatientById(patientId);

        assertNotNull(result);
        assertEquals(patientId, result.getId());
        assertEquals("Juan Perez", result.getName());
        verify(patientRepository, times(1)).findById(patientId);
    }

    @Test
    void testGetPatientByIdNotFound() {
        UUID missingId = UUID.randomUUID();
        when(patientRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> patientService.getPatientById(missingId));
    }

    @Test
    void testGetPatientsByStatus() {
        when(patientRepository.findByStatus(PatientStatus.NORMAL)).thenReturn(List.of(testPatient));

        List<PatientDTO> result = patientService.getPatientsByStatus(PatientStatus.NORMAL);

        assertEquals(1, result.size());
        assertEquals("Juan Perez", result.get(0).getName());
        verify(patientRepository, times(1)).findByStatus(PatientStatus.NORMAL);
    }
}
