package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum Impact {
    NONE, LOW, MEDIUM, HIGH;

    // Use Locale.ROOT for case conversion: in Turkish locale (tr_TR) the
    // default toUpperCase() converts "medium" to "MEDİUM" (dotted I), which
    // breaks valueOf() and crashes the client on the sync_origin_registry
    // payload decode. Reported by RadexKonera, 2026-04-23.
    public static final Codec<Impact> CODEC = Codec.STRING.xmap(
        s -> Impact.valueOf(s.toUpperCase(Locale.ROOT)),
        i -> i.name().toLowerCase(Locale.ROOT)
    );

    public int getDotCount() {
        return ordinal();
    }
}
