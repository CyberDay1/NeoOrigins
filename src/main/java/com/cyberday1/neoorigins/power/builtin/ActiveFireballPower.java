package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/** Shoots a small fireball in the direction the player is looking. */
public class ActiveFireballPower extends AbstractActivePower<ActiveFireballPower.Config> {

    public record Config(float speed, int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("speed", 1.5f).forGetter(Config::speed),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        SmallFireball fireball = new SmallFireball(level, player, look.scale(config.speed()));
        Vec3 spawn = player.getEyePosition().add(look.scale(2.0));
        fireball.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(fireball);
        return true;
    }
}
