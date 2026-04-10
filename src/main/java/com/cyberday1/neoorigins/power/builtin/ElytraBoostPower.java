package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Gives a firework-rocket-like speed burst while elytra gliding. */
public class ElytraBoostPower extends AbstractActivePower<ElytraBoostPower.Config> {

    public record Config(float strength, int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("strength", 1.5f).forGetter(Config::strength),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        if (!player.isFallFlying()) return false;

        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.add(
            look.x * 0.1 + (look.x * 1.5 - motion.x) * 0.5,
            look.y * 0.1 + (look.y * 1.5 - motion.y) * 0.5,
            look.z * 0.1 + (look.z * 1.5 - motion.z) * 0.5
        ).scale(config.strength()));
        player.hurtMarked = true; // sync velocity to client
        return true;
    }
}
