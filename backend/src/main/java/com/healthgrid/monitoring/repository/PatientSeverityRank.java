package com.healthgrid.monitoring.repository;

import java.util.UUID;

public interface PatientSeverityRank {

    UUID getPatientId();

    Integer getSeverityRank();
}
