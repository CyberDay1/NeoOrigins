package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.mixin.LivingEntityAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passive wall-climb: while mid-air and pressed against a wall, holding jump
 * propels the player upward; releasing jump causes a controlled slow-fall grip.
 *
 * <p>Matches the upstream {@code origins:climbing} power behaviour — not a toggle,
 * no skill slot required. Server-authoritative: sets delta movement each tick; the
 * client receives a position correction and renders the climb.
 */
public class WallClimbingPower extends PowerType<WallClimbingPower.Config> {

    public record Config(String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.horizontalCollision && !player.onGround()) {
            var delta = player.getDeltaMovement();
            if (((LivingEntityAccessor) player).neoorigins$isJumping()) {
                // Climb up while pressing jump against a wall.
                player.setDeltaMovement(delta.x, 0.2, delta.z);
            } else if (delta.y < 0) {
                // Grip the wall — slow fall when not pressing jump.
                player.setDeltaMovement(delta.x, Math.max(delta.y, -0.15), delta.z);
            }
        }
    }
}
