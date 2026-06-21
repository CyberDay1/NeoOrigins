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
        String type,
        String cooldownIcon,
        boolean alwaysShowIcon
    ) implements PowerConfiguration, HudIconConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        var abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) { abilities.mayfly = true; changed = true; }
        if (changed) player.onUpdateAbilities();
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
