package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Scales the knockback this player receives from any source.
 * 0.0 = immune, 1.0 = vanilla, 2.0 = double knockback.
 * Event handling via LivingKnockBackEvent in OriginEventHandler.
 */
public class KnockbackModifierPower extends PowerType<KnockbackModifierPower.Config> {

    public record Config(float multiplier, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("multiplier", 1.0f).forGetter(Config::multiplier),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
