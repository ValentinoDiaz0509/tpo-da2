package com.healthgrid.monitoring.model;

/**
 * Determina el status GENERAL de un paciente basado en alertas activas.
 * 
 * Lógica de Prioridad:
 * 1. Si hay alertas CRITICAL no reconocidas → CRITICAL
 * 2. Si hay alertas WARNING no reconocidas → WARNING
 * 3. Si todas las alertas están reconocidas → usar status del paciente
 * 4. Si no hay alertas → NORMAL
 */
public enum PatientStatus {
    NORMAL("El paciente está estable", 1),
    WARNING("El paciente requiere atención", 2),
    CRITICAL("El paciente requiere atención urgente", 3),
    INACTIVE("Monitoreo suspendido", 0);
    
    private final String description;
    private final int priority;
    
    PatientStatus(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getPriority() {
        return priority;
    }
}
