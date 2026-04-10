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
        Vec3 spawn = player.getEyePosition().add(look.scale(2.0));
        int count = 3 + player.getRandom().nextInt(2); // 3-4 fireballs
        for (int i = 0; i < count; i++) {
            // Add slight random spread to each fireball
            double spreadX = (player.getRandom().nextDouble() - 0.5) * 0.15;
            double spreadY = (player.getRandom().nextDouble() - 0.5) * 0.10;
            double spreadZ = (player.getRandom().nextDouble() - 0.5) * 0.15;
            Vec3 dir = look.add(spreadX, spreadY, spreadZ).normalize().scale(config.speed());
            SmallFireball fireball = new SmallFireball(level, player, dir);
            fireball.setPos(spawn.x, spawn.y, spawn.z);
            level.addFreshEntity(fireball);
        }
        return true;
    }
}
