package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;

public enum Impact {
    NONE, LOW, MEDIUM, HIGH;

    public static final Codec<Impact> CODEC = Codec.STRING.xmap(
        s -> Impact.valueOf(s.toUpperCase()),
        i -> i.name().toLowerCase()
    );

    public int getDotCount() {
        return ordinal();
    }
}
