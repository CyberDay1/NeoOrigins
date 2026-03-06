package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Launches the player straight upward — great paired with flight or elytra_boost for vertical takeoff. */
public class ActiveLaunchPower extends AbstractActivePower<ActiveLaunchPower.Config> {

    public record Config(float power, int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("power", 1.8f).forGetter(Config::power),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.x, config.power(), current.z);
        player.hurtMarked = true;
        return true;
    }
}
