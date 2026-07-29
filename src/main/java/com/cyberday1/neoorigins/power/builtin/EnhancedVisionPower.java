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
 * {@code LightTexture} mixin. Origins use it for exposure-style darkness
 * compensation — cat eyes, salamander, oculus drone, etc. — without the visual
 * baggage of a potion effect.
 *
 * <p><b>History, and how it's toggled now.</b> This power once extended
 * {@code AbstractTogglePower} so pack authors could assign a keybind to flip it
 * on and off. That put it in a skill slot, where the first stray skill keypress
 * after picking an origin silently switched it off and the capability-sync gate
 * silenced the mixin — testers reported "night vision doesn't work" on every
 * origin that had it. The power itself is therefore still plain always-on and
 * still claims no skill slot.
 *
 * <p>On/off control instead lives on a dedicated "Toggle Night Vision" keybind
 * (K by default) registered in the vanilla Controls menu — see
 * {@code NeoOriginsKeybindings#TOGGLE_NIGHT_VISION}. It flips one persisted
 * per-player flag ({@code PlayerOriginData#isNightVisionEnabled}) that gates
 * both night-vision paths at once: every {@code minecraft:night_vision}
 * persistent effect server-side, and this power's brightness boost client-side
 * in {@code LightTextureMixin}. The flag defaults to ON and survives relog and
 * death, so a player who never touches the key sees the always-on behaviour,
 * and no skill slot is consumed by any of it.
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
    public Set<String> capabilities(Config config) {
        // Global kill-switch (content.toml): when an admin disables enhanced
        // vision we withhold the capability entirely, so it never reaches the
        // client and the LightTexture brightness boost stays off.
        if (com.cyberday1.neoorigins.config.ContentTogglesConfig.isEnhancedVisionDisabled()) {
            return Set.of();
        }
        return CAPS;
    }
}
