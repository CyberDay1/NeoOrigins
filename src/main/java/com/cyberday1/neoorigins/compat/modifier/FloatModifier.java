package com.cyberday1.neoorigins.compat.modifier;

/**
 * A single-value float transform — used to compose Apoli-style "modifier" JSON
 * into runtime lambdas. Modifiers stack via composition in ModifierParser.
 *
 * <p>Usage pattern: parse once at power-load time, apply at event time.
 */
@FunctionalInterface
public interface FloatModifier {

    /** Apply this modifier to a base value and return the result. */
    float apply(float base);

    /** Identity modifier — returns the base value unchanged. */
    static FloatModifier identity() {
        return base -> base;
    }
}
