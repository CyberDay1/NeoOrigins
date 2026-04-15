package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * AoE ground slam — damages and knocks back all nearby entities.
 * The player slams the ground, sending a shockwave outward.
 */
public class ActiveGroundSlamPower extends AbstractActivePower<ActiveGroundSlamPower.Config> {

    public record Config(
        float damage,
        float knockbackStrength,
        double radius,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("damage", 6.0f).forGetter(Config::damage),
            Codec.FLOAT.optionalFieldOf("knockback_strength", 1.5f).forGetter(Config::knockbackStrength),
            Codec.DOUBLE.optionalFieldOf("radius", 6.0).forGetter(Config::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 120).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = player.position();

        AABB box = player.getBoundingBox().inflate(config.radius());
        var targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());

        if (targets.isEmpty()) return false;

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), config.damage());

            // Knockback away from player
            Vec3 dir = target.position().subtract(center).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(
                dir.x * config.knockbackStrength(),
                0.4 * config.knockbackStrength(),
                dir.z * config.knockbackStrength()));
            target.hurtMarked = true;
        }

        // Effects
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION,
            center.x, center.y + 0.5, center.z, 8, config.radius() * 0.4, 0.2, config.radius() * 0.4, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
            center.x, center.y + 0.1, center.z, 30, config.radius() * 0.5, 0.1, config.radius() * 0.5, 0.01);

        return true;
    }
}
