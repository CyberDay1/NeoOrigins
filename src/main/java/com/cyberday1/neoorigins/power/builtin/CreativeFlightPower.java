package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants free, creative-style flight (mayfly + flying) as a toggle, without the
 * extra phantom/spectator behaviour — the player keeps solid block collision,
 * normal visibility and gravity when not flying. Double-tap jump to take off,
 * jump/sneak to climb and descend, exactly like creative mode.
 *
 * <p>Unlike {@code neoorigins:flight} and {@code neoorigins:natural_glide}
 * (both elytra/fall-flying mechanics), this is true hover-flight — intended for
 * "ride the sword" / levitating-cultivator fantasies. The abilities are pushed
 * to the client each tick via {@code onUpdateAbilities()} to survive sync
 * races; {@link #removeEffect} restores survival defaults but never clears the
 * flags for a creative/spectator player (that would lock them out of their mode).
 */
public class CreativeFlightPower extends AbstractTogglePower<CreativeFlightPower.Config> {

    public record Config(
        boolean enabled,
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, HudIconConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            // Config kill-switch: a top-level "enabled":false (injected by the
            // power_overrides system) turns the flight off — the ability is
            // stripped each tick and never re-granted.
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Config::enabled),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        if (!config.enabled()) {
            // Disabled via power_overrides — strip any flight the player may still
            // hold and never re-grant it.
            removeEffect(player, config);
            return;
        }
        var abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) { abilities.mayfly = true; changed = true; }
        if (changed) player.onUpdateAbilities();
    }

    @Override
    protected void onToggledOn(ServerPlayer player, Config config) {
        // Keybind flipped the power on: actually take off, don't just arm mayfly.
        // Without this the player would still have to double-tap jump to lift —
        // which reads as "the toggle did nothing".
        if (!config.enabled()) return;
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
        // The client cancels `flying` while on the ground (LocalPlayer.aiStep),
        // so nudge the player up a hair and force-sync the velocity — that puts
        // them airborne for the tick, letting the flying flag stick.
        if (player.onGround()) {
            var m = player.getDeltaMovement();
            player.setDeltaMovement(m.x, 0.42, m.z);
            player.hurtMarked = true;
        }
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (player.isCreative() || player.isSpectator()) return;
        var abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }
}
