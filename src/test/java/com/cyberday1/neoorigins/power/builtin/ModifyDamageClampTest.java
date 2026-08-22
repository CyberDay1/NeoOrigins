package com.cyberday1.neoorigins.power.builtin;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Seer-pack keystone regression: Apoli total-clamp operations on
 * modify_damage_taken must clamp the damage value, NOT collapse to a multiplier.
 *
 * <p>{@code max_total 4} is an upper CAP (Math.min) and {@code min_total} a lower
 * FLOOR (Math.max). The old compat path turned {@code max_total 4} into a
 * {@code (1.0 + 4) = 5x} multiplier, so a player taking 10 damage took 50 — they
 * died FASTER, before the astral-projection teleport could fire.
 */
class ModifyDamageClampTest {

    private static ModifyDamagePower.Config cfg(Optional<Float> set, Optional<Float> max, Optional<Float> min) {
        return new ModifyDamagePower.Config(
            ModifyDamagePower.Direction.IN, 1.0f,
            Optional.empty(), Optional.empty(), Optional.empty(),
            set, max, min, "neoorigins:modify_damage");
    }

    @Test
    void maxTotalCapsDamage() {
        // 10 incoming, capped at 4 → 4 (NOT 50 from the old 5x multiplier bug).
        assertEquals(4.0f, cfg(Optional.empty(), Optional.of(4.0f), Optional.empty()).apply(10.0f));
        // Below the cap is unaffected.
        assertEquals(2.0f, cfg(Optional.empty(), Optional.of(4.0f), Optional.empty()).apply(2.0f));
    }

    @Test
    void minTotalFloorsDamage() {
        assertEquals(4.0f, cfg(Optional.empty(), Optional.empty(), Optional.of(4.0f)).apply(1.0f));
        assertEquals(9.0f, cfg(Optional.empty(), Optional.empty(), Optional.of(4.0f)).apply(9.0f));
    }

    @Test
    void setTotalReplacesValue() {
        assertEquals(0.0f, cfg(Optional.of(0.0f), Optional.empty(), Optional.empty()).apply(123.0f));
    }

    @Test
    void plainMultiplierStillWorks() {
        ModifyDamagePower.Config doubled = new ModifyDamagePower.Config(
            ModifyDamagePower.Direction.IN, 2.0f,
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), "neoorigins:modify_damage");
        assertEquals(20.0f, doubled.apply(10.0f));
    }
}
