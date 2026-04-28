package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Percentage chance to completely dodge incoming damage. When triggered,
 * the damage event is cancelled entirely.
 *
 * <p>Applied via the damage event handler in CombatPowerEvents.
 *
 * <p>Use cases: Inchling (too small to hit), Tiny (evasion),
 * Phantom (spectral dodge).
 */
public class DodgeChancePower extends PowerType<DodgeChancePower.Config> {

    public record Config(
        float chance,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("chance", 0.15F).forGetter(Config::chance),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
