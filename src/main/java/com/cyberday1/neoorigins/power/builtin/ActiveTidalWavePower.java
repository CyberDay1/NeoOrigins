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
 * Cone-shaped water blast in the player's look direction.
 * Knocks back and damages entities caught in the wave.
 */
public class ActiveTidalWavePower extends AbstractActivePower<ActiveTidalWavePower.Config> {

    public record Config(
        float damage,
        float knockbackStrength,
        double range,
        double coneAngle,
        int cooldownTicks,
        int hungerCost,
        String type
    ) implements AbstractActivePower.Config {
        @Override public int cooldownTicks() { return cooldownTicks; }
        @Override public int hungerCost() { return hungerCost; }

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("damage", 4.0f).forGetter(Config::damage),
            Codec.FLOAT.optionalFieldOf("knockback_strength", 2.0f).forGetter(Config::knockbackStrength),
            Codec.DOUBLE.optionalFieldOf("range", 8.0).forGetter(Config::range),
            Codec.DOUBLE.optionalFieldOf("cone_angle", 60.0).forGetter(Config::coneAngle),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double cosThreshold = Math.cos(Math.toRadians(config.coneAngle() / 2.0));

        AABB box = player.getBoundingBox().inflate(config.range());
        var targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());

        int hit = 0;
        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().add(0, target.getBbHeight() / 2, 0).subtract(eye);
            double dist = toTarget.length();
            if (dist > config.range() || dist < 0.5) continue;

            // Cone check — dot product of normalized vectors
            double dot = toTarget.normalize().dot(look);
            if (dot < cosThreshold) continue;

            target.hurt(player.damageSources().playerAttack(player), config.damage());

            // Push in look direction
            Vec3 push = look.scale(config.knockbackStrength());
            target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.3, push.z));
            target.hurtMarked = true;
            hit++;
        }

        // Effects regardless of hits — the wave is visible
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f, 0.8f);
        for (int i = 1; i <= 4; i++) {
            Vec3 particlePos = eye.add(look.scale(i * 2.0));
            level.sendParticles(ParticleTypes.SPLASH,
                particlePos.x, particlePos.y - 0.5, particlePos.z,
                20, 0.8 * i, 0.3, 0.8 * i, 0.05);
            level.sendParticles(ParticleTypes.BUBBLE,
                particlePos.x, particlePos.y, particlePos.z,
                10, 0.5 * i, 0.3, 0.5 * i, 0.02);
        }

        return hit > 0;
    }
}
