package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Toggleable mid-air launch into elytra-style flight, activated by a jump in mid-air
 * ({@code AirJumpPayload}) rather than by the natural-glide path.
 *
 * <p>Like {@code neoorigins:natural_glide}, the flight runs with an empty chest slot, so
 * vanilla draws no wings. {@code render_elytra} opts into the cosmetic wings through the
 * same capability encoding ({@link ElytraFlightPower#addRenderCaps}), and defaults to
 * {@code false} so existing packs keep the wingless look.
 */
public class FlightPower extends AbstractTogglePower<FlightPower.Config> {

    private static final FlightPower INSTANCE = new FlightPower();

    /** Returns true if the player has the flight power granted AND toggled on. */
    public static boolean isActive(ServerPlayer player) {
        return ActiveOriginService.has(player, FlightPower.class,
            config -> !INSTANCE.isToggledOff(player, config));
    }

    public record Config(String type,
        String cooldownIcon,
        boolean alwaysShowIcon,
        boolean renderElytra,
        String textureLocation) implements PowerConfiguration, HudIconConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon),
            Codec.BOOL.optionalFieldOf("render_elytra", false).forGetter(Config::renderElytra),
            Codec.STRING.optionalFieldOf("texture_location", "").forGetter(Config::textureLocation)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        Set<String> caps = new HashSet<>();
        caps.add("flight");
        ElytraFlightPower.addRenderCaps(caps, config.renderElytra(), config.textureLocation());
        return caps;
    }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        // Flight activation is handled via AirJumpPayload (client sends packet on mid-air jump).
        // This tick keeps the power active for the mixin to detect.
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
    }
}
