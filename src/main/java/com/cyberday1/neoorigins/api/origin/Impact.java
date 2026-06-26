package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum Impact {
    NONE, LOW, MEDIUM, HIGH;

    /** Accepts both string ("low") and integer (1) forms for Origins compat. */
    public static final Codec<Impact> CODEC = Codec.either(Codec.STRING, Codec.INT).xmap(
        either -> either.map(
            Impact::fromString,
            Impact::fromInt
        ),
        i -> com.mojang.datafixers.util.Either.left(i.name().toLowerCase(Locale.ROOT))
    );

    /** Maps an integer level to an Impact, clamping out-of-range values to NONE. */
    private static Impact fromInt(int i) {
        return i >= 0 && i < values().length ? values()[i] : NONE;
    }

    /**
     * Parses the STRING branch tolerantly. Real Apoli packs commonly write the
     * numeric impact form quoted ({@code "impact": "2"}); the Either's STRING
     * branch wins for those, so a plain {@code valueOf} would throw
     * "No enum constant Impact.2" and fail the whole origin. Fall back to the
     * same int→enum mapping the INT branch uses.
     */
    private static Impact fromString(String s) {
        try {
            return Impact.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notAName) {
            try {
                return fromInt(Integer.parseInt(s.trim()));
            } catch (NumberFormatException notANumber) {
                return NONE;
            }
        }
    }

    public int getDotCount() {
        return ordinal();
    }
}
