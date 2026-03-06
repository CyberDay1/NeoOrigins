package com.cyberday1.neoorigins.compat.condition;

public enum ComparisonType {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN_OR_EQUAL(">="),
    GREATER_THAN(">");

    private final String key;

    ComparisonType(String key) { this.key = key; }

    public static ComparisonType fromString(String s) {
        for (ComparisonType c : values()) {
            if (c.key.equals(s)) return c;
        }
        return EQUAL;
    }

    public boolean test(double a, double b) {
        return switch (this) {
            case LESS_THAN             -> a < b;
            case LESS_THAN_OR_EQUAL    -> a <= b;
            case EQUAL                 -> a == b;
            case NOT_EQUAL             -> a != b;
            case GREATER_THAN_OR_EQUAL -> a >= b;
            case GREATER_THAN          -> a > b;
        };
    }
}
