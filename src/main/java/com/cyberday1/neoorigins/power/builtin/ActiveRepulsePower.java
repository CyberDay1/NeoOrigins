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
 * AoE force blast — pushes all nearby entities away from the player.
 * Pure knockback with no damage.
 */
public class ActiveRepulsePower extends AbstractActivePower<ActiveRepulsePower.Config> {

    public record Config(
        float knockbackStrength,
        double radius,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("knockback_strength", 2.5f).forGetter(Config::knockbackStrength),
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(Config::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 80).forGetter(Config::cooldownTicks),
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
            Vec3 dir = target.position().subtract(center).normalize();
            double dist = target.distanceTo(player);
            // Stronger push for closer entities
            double strength = config.knockbackStrength() * (1.0 + (config.radius() - dist) / config.radius());
            target.setDeltaMovement(target.getDeltaMovement().add(
                dir.x * strength, 0.5 * strength, dir.z * strength));
            target.hurtMarked = true;
        }

        // Effects — gravity distortion visual
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4f, 1.8f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            center.x, center.y + 1.0, center.z,
            40, config.radius() * 0.3, 0.5, config.radius() * 0.3, 0.5);
        level.sendParticles(ParticleTypes.EXPLOSION,
            center.x, center.y + 0.5, center.z,
            5, 0.5, 0.3, 0.5, 0.0);

        return true;
    }
}
