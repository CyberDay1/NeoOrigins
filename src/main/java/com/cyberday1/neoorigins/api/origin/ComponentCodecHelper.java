package com.cyberday1.neoorigins.api.origin;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

public final class ComponentCodecHelper {
    /** Codec that accepts a translation key string and wraps it as a translatable Component. */
    public static final Codec<Component> CODEC = Codec.STRING.xmap(
        Component::translatable,
        component -> component.getString() // fallback; will lose translation key
    );

    private ComponentCodecHelper() {}
}
