package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum Impact {
    NONE, LOW, MEDIUM, HIGH;

    /** Accepts both string ("low") and integer (1) forms for Origins compat. */
    public static final Codec<Impact> CODEC = Codec.either(Codec.STRING, Codec.INT).xmap(
        either -> either.map(
            s -> Impact.valueOf(s.toUpperCase(Locale.ROOT)),
            i -> i >= 0 && i < values().length ? values()[i] : NONE
        ),
        i -> com.mojang.datafixers.util.Either.left(i.name().toLowerCase(Locale.ROOT))
    );

    public int getDotCount() {
        return ordinal();
    }
}
