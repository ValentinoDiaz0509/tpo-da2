package com.healthgrid.monitoring.controller;

import com.healthgrid.monitoring.dto.PatientDTO;
import com.healthgrid.monitoring.model.PatientStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientControllerTest {

    @Test
    void testPatientDTOCreation() {
        UUID patientId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        PatientDTO patientDTO = PatientDTO.builder()
            .id(patientId)
            .name("Juan Perez")
            .room("101")
            .bed("A")
            .status(PatientStatus.NORMAL)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals(patientId, patientDTO.getId());
        assertEquals("Juan Perez", patientDTO.getName());
        assertEquals("101", patientDTO.getRoom());
        assertEquals("A", patientDTO.getBed());
        assertEquals(PatientStatus.NORMAL, patientDTO.getStatus());
    }
}
