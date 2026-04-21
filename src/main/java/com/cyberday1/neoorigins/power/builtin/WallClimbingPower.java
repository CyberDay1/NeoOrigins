package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Passive wall-climb. All movement behaviour is implemented by
 * {@link com.cyberday1.neoorigins.mixin.LivingEntityClimbMixin}, which hooks
 * {@code LivingEntity.onClimbable()} and returns true when the player has this
 * power granted and is pressed against a wall. Vanilla ladder/vine physics then
 * provides jump-to-ascend and grip-to-slow-fall for free.
 *
 * <p>Matches the upstream {@code origins:climbing} power behaviour — not a toggle,
 * no skill slot required.
 */
public class WallClimbingPower extends PowerType<WallClimbingPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }
}
