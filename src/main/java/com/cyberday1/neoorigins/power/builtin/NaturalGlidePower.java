package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
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
 * <p>Because the glide runs with an empty chest slot, vanilla draws no wings: its
 * elytra layer keys off the equipped item. Set {@code render_elytra} to opt into the
 * cosmetic wings, which is the same field {@code neoorigins:elytra_flight} carries and
 * goes through the same capability encoding ({@link ElytraFlightPower#addRenderCaps}).
 * It defaults to {@code false} here so packs written against the wingless behaviour
 * keep it; {@code elytra_flight} is the type that defaults it on.
 *
 * <p>Used by Phantom (spectral wings). Combine with
 * {@code neoorigins:elytra_boost} for a full glide + launch-boost kit.
 */
public class NaturalGlidePower extends PowerType<NaturalGlidePower.Config> {

    public record Config(String type, boolean renderElytra, String textureLocation)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.BOOL.optionalFieldOf("render_elytra", false).forGetter(Config::renderElytra),
            Codec.STRING.optionalFieldOf("texture_location", "").forGetter(Config::textureLocation)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        Set<String> caps = new HashSet<>();
        caps.add("natural_glide");
        ElytraFlightPower.addRenderCaps(caps, config.renderElytra(), config.textureLocation());
        return caps;
    }
}
