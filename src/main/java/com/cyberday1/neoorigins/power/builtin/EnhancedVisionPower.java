package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Passive low-light vision: emits the {@code "enhanced_vision"} capability tag while granted.
 *
 * <p>Unlike the full {@code minecraft:night_vision} status effect (screen tint, HUD icon,
 * max-level ramp at end of duration), this power scales the player's brightness curve
 * directly via a client-side lightmap mixin. Origins can use it for exposure-style
 * darkness compensation — cat eyes, salamander, oculus drone, etc. — without the visual
 * baggage of a potion effect.
 *
 * <p>All exposure work happens on the logical client; the server never evaluates this
 * power beyond publishing the capability tag. The {@code exposure} field is currently
 * advisory — the v1 client mixin hardcodes 0.7. If runtime playtest shows it needs
 * per-origin variance, wire it through a client-synced power-config payload.
 */
public class EnhancedVisionPower extends PowerType<EnhancedVisionPower.Config> {

    private static final Set<String> CAPS = Set.of("enhanced_vision");

    public record Config(float exposure, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("exposure", 0.7F).forGetter(Config::exposure),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }
}
