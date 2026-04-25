package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Passive wall-climb: emits the {@code "wall_climb"} capability tag while granted.
 *
 * <p>All movement behavior is implemented by {@code LivingEntityClimbMixin}, which
 * hooks {@code LivingEntity.onClimbable()} and returns true when the capability is
 * active and the player is pressed against a wall. Vanilla ladder/vine physics then
 * provides jump-to-ascend and grip-to-slow-fall for free, in lockstep on both sides.
 *
 * <p>Matches the upstream {@code origins:climbing} power behaviour — not a toggle,
 * no skill slot required.
 */
public class WallClimbingPower extends PowerType<WallClimbingPower.Config> {

    private static final Set<String> CAPS = Set.of("wall_climb");

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }
}
