package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public final class ComponentCodecHelper {
    /**
     * Accepts either form, so both shipped origin JSONs and the 2.1 creator's
     * output round-trip: a raw string is treated as a translation key (built-in
     * origins ship {@code "origins.neoorigins.human.name"} and rely on the
     * Language lookup), and any vanilla component JSON object —
     * {@code {"text":"…"}}, {@code {"translate":"…"}}, etc. — decodes through
     * {@link ComponentSerialization#CODEC}. The Origin Creator emits
     * {@code {"text":"…"}} so author-entered names render literally, which used
     * to fail validation with "Not a string".
     */
    public static final Codec<Component> CODEC = Codec.withAlternative(
        Codec.STRING.xmap(
            Component::translatable,
            component -> component.getString() // legacy encode; loses translation key
        ),
        ComponentSerialization.CODEC
    );

    private ComponentCodecHelper() {}
}
