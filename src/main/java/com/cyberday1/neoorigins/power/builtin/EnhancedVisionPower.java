package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * Always-on low-light vision: emits the {@code "enhanced_vision"} capability tag
 * for every player who has the power granted.
 *
 * <p>Unlike {@code minecraft:night_vision} (screen tint, HUD icon, max-level ramp
 * at end of duration), this scales the brightness curve directly via a client-side
 * lightmap mixin. Origins use it for exposure-style darkness compensation —
 * cat eyes, salamander, oculus drone, etc. — without the visual baggage of a
 * potion effect.
 *
 * <p>Previously extended {@code AbstractTogglePower} so pack authors could assign
 * a keybind to flip it on and off. In practice that meant the power occupied a
 * skill slot and got toggled off by accidental keypress during normal gameplay,
 * and tester reports said "night vision doesn't work" on every origin that had
 * it — the first skill-key press after picking the origin turned it off and the
 * capability-sync gate silenced the mixin. Making it plain always-on is the
 * pragmatic fix; a dedicated toggle power can be layered on top if a pack
 * genuinely needs on/off control.
 *
 * <p>The {@code exposure} field is retained in the schema for forward-compat but
 * is currently advisory — the client mixin hardcodes 0.7. Wire through a synced
 * payload if runtime playtest shows per-origin variance is needed.
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
