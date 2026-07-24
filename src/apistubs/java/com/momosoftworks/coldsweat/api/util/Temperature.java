package com.momosoftworks.coldsweat.api.util;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;

/**
 * Compile-only API stub for Cold Sweat's ({@code cold_sweat}) {@code Temperature}
 * facade. Minimal signature stand-in carrying ONLY the surface the bridge uses —
 * the three static get/set/add methods and the nested {@link Trait} enum — with
 * the EXACT real descriptors ({@code javap}-verified against ColdSweat-2.4.2 for
 * MC 1.21.1). Never bundled and never on the runtime classpath; the real class
 * loads at runtime when {@code cold_sweat} is present.
 *
 * <p>The one and only class that references these types is
 * {@link com.cyberday1.neoorigins.compat.coldsweat.ColdSweatBridge}, which is
 * isolated behind a {@code ModList.isLoaded("cold_sweat")} gate — so the mod runs
 * cleanly without Cold Sweat and this stub leaves zero runtime footprint. If the
 * bridge starts using more of the API, mirror those signatures here (verify
 * descriptors with {@code javap} against the real jar).
 */
public class Temperature {

    /** Read a temperature trait for the entity. */
    public static double get(LivingEntity entity, Trait trait) {
        return 0.0;
    }

    /** Overwrite a temperature trait to an absolute value. */
    public static void set(LivingEntity entity, Trait trait, double value) {
    }

    /** Add a delta to a temperature trait (positive warms, negative cools). */
    public static void add(LivingEntity entity, Trait trait, double value) {
    }

    /**
     * Cold Sweat's {@code Temperature.Trait} enum. Only the constants the bridge
     * maps to are needed; the {@code StringRepresentable} implementation +
     * {@code fromID} mirror the real enum's serialization surface.
     */
    public enum Trait implements StringRepresentable {
        WORLD,
        CORE,
        BASE,
        BODY,
        RATE,
        FREEZING_POINT,
        BURNING_POINT,
        COLD_RESISTANCE,
        HEAT_RESISTANCE,
        COLD_DAMPENING,
        HEAT_DAMPENING;

        public static Trait fromID(String id) {
            return null;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }
}
