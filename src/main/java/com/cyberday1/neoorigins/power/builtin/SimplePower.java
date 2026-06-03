package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Does nothing. A display-only marker power, mirroring {@code origins:simple}
 * from the original Origins mod.
 *
 * <p>It has no gameplay effect, no capabilities, and no lifecycle behavior —
 * its only purpose is to appear as an entry in the origin GUI so a pack author
 * can attach a {@code name} + {@code description} (the heading + body text that
 * every power carries) without also granting an ability. Use it for flavor
 * text, lore lines, or to describe an effect that is implemented elsewhere
 * (e.g. by a mixin, datapack, or another mod).
 *
 * <p>The compat layer routes {@code origins:simple} / {@code apace:simple} here
 * when no known id-override applies (see {@code OriginsPowerTranslator}).
 */
public class SimplePower extends PowerType<SimplePower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
