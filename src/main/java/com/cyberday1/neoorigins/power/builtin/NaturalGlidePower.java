package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Grants the player elytra-style gliding without needing to equip an elytra.
 * Pressing space while falling starts the fall-flying state exactly as a
 * vanilla elytra would, ignoring the chest-slot item check.
 *
 * <p>Emits the {@code natural_glide} capability tag. The corresponding
 * {@code PlayerStartFallFlyingMixin} reads this tag at the head of
 * {@code tryToStartFallFlying} and bypasses the standard elytra-item check.
 *
 * <p>Used by Phantom (spectral wings). Combine with
 * {@code neoorigins:elytra_boost} for a full glide + launch-boost kit.
 */
public class NaturalGlidePower extends PowerType<NaturalGlidePower.Config> {

    private static final Set<String> CAPS = Set.of("natural_glide");

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
