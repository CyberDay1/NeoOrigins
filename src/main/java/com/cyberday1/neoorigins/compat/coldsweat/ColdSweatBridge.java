package com.cyberday1.neoorigins.compat.coldsweat;

import com.cyberday1.neoorigins.NeoOrigins;

import com.momosoftworks.coldsweat.api.util.Temperature;

import net.minecraft.world.entity.LivingEntity;

/**
 * The one and only class that references Cold Sweat ({@code cold_sweat}) types.
 * Every symbol here resolves against the compile-only {@code cold_sweat} API stub
 * (see {@code src/apistubs/java/com/momosoftworks/coldsweat/api/util/Temperature.java});
 * the class is isolated behind a {@code ModList.isLoaded("cold_sweat")} gate in
 * {@link com.cyberday1.neoorigins.compat.action.BuiltinActions} (the
 * {@code neoorigins:modify_temperature} action) and
 * {@link com.cyberday1.neoorigins.compat.condition.BuiltinConditions} (the
 * {@code neoorigins:body_temperature} condition) — it is never class-loaded when Cold
 * Sweat is absent, which keeps {@code NoClassDefFoundError} off the table on
 * servers without the mod.
 *
 * <p>Both entry points map the author-facing JSON trait string (e.g. {@code core},
 * {@code heat_resistance}) to a {@link Temperature.Trait} constant here — the only
 * place that vocabulary meets Cold Sweat's enum. An unknown trait string is logged
 * and treated as {@code CORE} (read) / a no-op (write) rather than throwing.
 */
public final class ColdSweatBridge {

    private ColdSweatBridge() {}

    /**
     * Map a NeoOrigins JSON trait string onto Cold Sweat's {@link Temperature.Trait}.
     * Returns {@code null} for an unrecognized name so callers can fail closed.
     */
    private static Temperature.Trait trait(String traitName) {
        if (traitName == null) return null;
        return switch (traitName.toLowerCase()) {
            case "core"            -> Temperature.Trait.CORE;
            case "base"            -> Temperature.Trait.BASE;
            case "world"           -> Temperature.Trait.WORLD;
            case "body"            -> Temperature.Trait.BODY;
            case "rate"            -> Temperature.Trait.RATE;
            case "freezing_point"  -> Temperature.Trait.FREEZING_POINT;
            case "burning_point"   -> Temperature.Trait.BURNING_POINT;
            case "cold_resistance" -> Temperature.Trait.COLD_RESISTANCE;
            case "heat_resistance" -> Temperature.Trait.HEAT_RESISTANCE;
            case "cold_dampening"  -> Temperature.Trait.COLD_DAMPENING;
            case "heat_dampening"  -> Temperature.Trait.HEAT_DAMPENING;
            default                -> null;
        };
    }

    /**
     * Read the player's temperature for {@code traitName} via Cold Sweat. An
     * unknown trait falls back to {@code CORE} with a logged warning (rather than
     * throwing), so a typo degrades to a defined reading instead of a hard error.
     */
    public static double get(LivingEntity entity, String traitName) {
        Temperature.Trait t = trait(traitName);
        if (t == null) {
            NeoOrigins.LOGGER.warn(
                "[Cold Sweat] unknown temperature trait '{}' — reading 'core' instead", traitName);
            t = Temperature.Trait.CORE;
        }
        return Temperature.get(entity, t);
    }

    /**
     * Modify the player's temperature. When {@code set} is true the value is an
     * absolute overwrite ({@code Temperature.set}); otherwise it is added as a
     * delta ({@code Temperature.add}). An unknown trait is a logged no-op.
     */
    public static void modify(LivingEntity entity, String traitName, double amount, boolean set) {
        Temperature.Trait t = trait(traitName);
        if (t == null) {
            NeoOrigins.LOGGER.warn(
                "[Cold Sweat] modify_temperature: unknown temperature trait '{}' — doing nothing", traitName);
            return;
        }
        if (set) {
            Temperature.set(entity, t, amount);
        } else {
            Temperature.add(entity, t, amount);
        }
    }
}
