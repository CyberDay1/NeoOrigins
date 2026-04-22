package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Toggleable low-light vision: emits the {@code "enhanced_vision"} capability tag while
 * granted and not toggled off.
 *
 * <p>Unlike the full {@code minecraft:night_vision} status effect (screen tint, HUD icon,
 * max-level ramp at end of duration), this power scales the player's brightness curve
 * directly via a client-side lightmap mixin. Origins can use it for exposure-style
 * darkness compensation — cat eyes, salamander, oculus drone, etc. — without the visual
 * baggage of a potion effect.
 *
 * <p>The basic {@code neoorigins:night_vision} power (persistent_effect alias) is
 * deliberately always-on with no toggle. This power takes the toggle role: pack authors
 * hand the player a keybind so they can flip the exposure boost on and off. The toggle
 * state is synced to the client via {@code SyncActivePowersPayload}; when toggled off
 * the capability tag disappears from {@code ClientActivePowers} and the LightTexture
 * mixin stops brightening automatically.
 *
 * <p>All exposure work happens on the logical client; the server never evaluates this
 * power beyond publishing the capability tag. The {@code exposure} field is currently
 * advisory — the v1 client mixin hardcodes 0.7. If runtime playtest shows it needs
 * per-origin variance, wire it through a client-synced power-config payload.
 */
public class EnhancedVisionPower extends AbstractTogglePower<EnhancedVisionPower.Config> {

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

    // No server-side effect to apply/remove — the capability tag alone drives the
    // client mixin. AbstractTogglePower's isToggledOff is what we gate on, and
    // ActiveOriginService.hasCapability already respects that gate.
    @Override protected void tickEffect(ServerPlayer player, Config config) {}
    @Override protected void removeEffect(ServerPlayer player, Config config) {}
}
