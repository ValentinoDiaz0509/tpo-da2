package com.healthgrid.monitoring.model;

/**
 * Enum para los niveles de severidad de alertas
 */
public enum AlertSeverity {
    /**
     * Información - Para registro sin acción inmediata necesaria
     */
    INFO("Info", 1),

    /**
     * Advertencia - Requiere revisión del personal médico
     */
    WARNING("Warning", 2),

    /**
     * Crítico - Requiere atención inmediata
     */
    CRITICAL("Critical", 3);

    private final String displayName;
    private final int level;

    AlertSeverity(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }
}
