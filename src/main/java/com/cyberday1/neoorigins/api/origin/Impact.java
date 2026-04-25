package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum Impact {
    NONE, LOW, MEDIUM, HIGH;

    public static final Codec<Impact> CODEC = Codec.STRING.xmap(
        s -> Impact.valueOf(s.toUpperCase(Locale.ROOT)),
        i -> i.name().toLowerCase(Locale.ROOT)
    );

    public int getDotCount() {
        return ordinal();
    }
}
