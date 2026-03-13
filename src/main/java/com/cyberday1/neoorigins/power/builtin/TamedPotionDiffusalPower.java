package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * When the player receives a positive potion effect, also applies it to nearby tamed animals.
 * Handled via MobEffectEvent.Added in CombatPowerEvents.
 */
public class TamedPotionDiffusalPower extends PowerType<TamedPotionDiffusalPower.Config> {

    public record Config(double radius, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("radius", 16.0).forGetter(Config::radius),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
