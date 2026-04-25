package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Lets the player look at an Enderman without provoking it. Emits the
 * {@code "ender_gaze"} capability tag, which {@code EnderManLookMixin}
 * reads from inside {@code EnderMan.isLookingAtMe} to short-circuit the
 * gaze-detection check.
 *
 * <p>Pure data holder — no server-side effect beyond the capability
 * declaration. Equivalent in role to wearing a carved pumpkin on the
 * head, minus the helmet slot cost and the visual obstruction.
 */
public class EnderGazeImmunityPower extends PowerType<EnderGazeImmunityPower.Config> {

    private static final Set<String> CAPS = Set.of("ender_gaze");

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
