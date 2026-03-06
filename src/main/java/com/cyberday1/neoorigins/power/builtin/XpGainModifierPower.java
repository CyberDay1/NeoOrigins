package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Scales XP received by the player from all sources.
 * Event handling via PlayerXpGainEvent in OriginEventHandler.
 */
public class XpGainModifierPower extends PowerType<XpGainModifierPower.Config> {

    public record Config(float multiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("multiplier", 1.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
