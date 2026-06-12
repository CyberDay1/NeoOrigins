package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.power.builtin.base.HudIconConfig;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public class FlightPower extends AbstractTogglePower<FlightPower.Config> {

    private static final FlightPower INSTANCE = new FlightPower();
    private static final Set<String> CAPS = Set.of("flight");

    /** Returns true if the player has the flight power granted AND toggled on. */
    public static boolean isActive(ServerPlayer player) {
        return ActiveOriginService.has(player, FlightPower.class,
            config -> !INSTANCE.isToggledOff(player, config));
    }

    public record Config(String type,
        String cooldownIcon,
        boolean alwaysShowIcon) implements PowerConfiguration, HudIconConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type),
            Codec.STRING.optionalFieldOf("cooldown_icon", "").forGetter(Config::cooldownIcon),
            Codec.BOOL.optionalFieldOf("always_show_icon", false).forGetter(Config::alwaysShowIcon)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) { return CAPS; }

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
