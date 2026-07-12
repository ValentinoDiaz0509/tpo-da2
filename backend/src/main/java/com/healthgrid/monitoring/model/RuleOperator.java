package com.healthgrid.monitoring.model;

/**
 * Enum para los operadores de comparación en reglas
 */
public enum RuleOperator {
    /**
     * Mayor que (>)
     */
    GREATER_THAN(">"),

    /**
     * Mayor o igual que (>=)
     */
    GREATER_THAN_OR_EQUAL(">="),

    /**
     * Menor que (<)
     */
    LESS_THAN("<"),

    /**
     * Menor o igual que (<=)
     */
    LESS_THAN_OR_EQUAL("<="),

    /**
     * Igual a (==)
     */
    EQUAL("=="),

    /**
     * No igual a (!=)
     */
    NOT_EQUAL("!=");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
