package com.cyberday1.neoorigins.compat;

import java.util.List;

/**
 * Combines a list of Apoli-shape modifier entries into a single multiplier
 * applied to a base value, mirroring Mojang's
 * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation}
 * semantics:
 *
 * <ul>
 *   <li>{@code addition} — added to the base value</li>
 *   <li>{@code multiply_base} / {@code multiply_total} — collapsed via
 *       {@code base + base * Σ value} so a single 0.5 modifier yields 1.5×;
 *       Apoli treats the two operations identically when there is no
 *       attribute system to layer them on top of (which is the case
 *       outside vanilla attributes — i.e. our XP / lava-speed hooks).</li>
 * </ul>
 *
 * <p>This is a pragmatic single-pass collapse, not a faithful reproduction
 * of Mojang's two-stage attribute pipeline; pack authors using both
 * operations on the same numeric stat get the same final number Apoli
 * produces because Apoli's own implementation runs them in one pass.
 */
public final class OriginsModifierMath {

    private OriginsModifierMath() {}

    public record Modifier(String operation, double value) {}

    /**
     * Apply all entries to the base value.
     *
     * @param base    starting value (e.g. 0.02f for lava speed, 1 for XP orb count)
     * @param entries Apoli modifier list
     * @return modified value
     */
    public static double apply(double base, List<Modifier> entries) {
        if (entries == null || entries.isEmpty()) return base;
        double additions = 0.0;
        double multiplierSum = 0.0;
        for (Modifier m : entries) {
            String op = m.operation() == null ? "addition" : m.operation().toLowerCase();
            switch (op) {
                case "addition" -> additions += m.value();
                case "multiply_base", "multiply_total" -> multiplierSum += m.value();
                default -> additions += m.value();
            }
        }
        double afterAdd = base + additions;
        return afterAdd + afterAdd * multiplierSum;
    }
}
