package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Passive thorns — when the player takes melee damage, the attacker
 * takes a configurable amount of damage back. Can optionally set the
 * attacker on fire.
 *
 * <p>Applied via the damage event handler in CombatPowerEvents.
 */
public class ThornsOnHitPower extends PowerType<ThornsOnHitPower.Config> {

    public record Config(
        float damage,
        int fireTicks,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("damage", 2.0F).forGetter(Config::damage),
            Codec.INT.optionalFieldOf("fire_ticks", 0).forGetter(Config::fireTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
