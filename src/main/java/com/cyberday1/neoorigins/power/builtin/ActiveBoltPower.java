package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Shoots a dragon-fire bolt (purple ender-breath projectile) in the player's look direction.
 * On impact it creates an area of dragon's breath that deals damage over time.
 */
public class ActiveBoltPower extends AbstractActivePower<ActiveBoltPower.Config> {

    public record Config(float speed, int cooldownTicks, String type) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("speed", 1.2f).forGetter(Config::speed),
            Codec.INT.optionalFieldOf("cooldown_ticks", 80).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        DragonFireball bolt = new DragonFireball(level, player, look.scale(config.speed()));
        Vec3 spawn = player.getEyePosition().add(look.scale(2.0));
        bolt.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(bolt);
        return true;
    }
}
