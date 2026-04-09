package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public class WallClimbingPower extends AbstractTogglePower<WallClimbingPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected void tickEffect(ServerPlayer player, Config config) {
        // Spider-climb: if player is touching a wall mid-air, slow their fall velocity
        if (player.horizontalCollision && !player.onGround()) {
            var delta = player.getDeltaMovement();
            if (delta.y < 0) {
                // Clamp downward speed to simulate climbing
                player.setDeltaMovement(delta.x, Math.max(delta.y, -0.15), delta.z);
            }
        }
    }

    @Override
    protected void removeEffect(ServerPlayer player, Config config) {}
}
