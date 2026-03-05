package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public class FlightPower extends PowerType<FlightPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {}

    @Override
    public void onRevoked(ServerPlayer player, Config config) {}

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Allow elytra glide without wearing an elytra.
        // Activate when the player is airborne, descending, and not already gliding.
        if (!player.onGround()
                && !player.isFallFlying()
                && !player.isInWater()
                && !player.isPassenger()
                && !player.isSpectator()
                && player.getDeltaMovement().y < 0) {
            player.startFallFlying();
        }
    }
}
