package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.service.MinionTracker;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Active power that commands all tamed mobs (via TameMobPower) to attack
 * the entity the player is looking at.
 */
public class CommandPackPower extends AbstractActivePower<CommandPackPower.Config> {

    public record Config(
        double range,
        int cooldownTicks,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(Config::cooldownTicks),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Find the entity the player is looking at
        Entity target = getTargetEntity(player, config.range());
        if (target == null || !(target instanceof LivingEntity livingTarget)) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.command_pack.no_target").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        // Don't allow commanding pack to attack the owner
        if (target == player) return false;

        // Get all tamed mobs for this player
        var tamed = MinionTracker.getAlive(player.getUUID(), TameMobPower.tamedMobKey());
        if (tamed.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.command_pack.no_pack").withStyle(ChatFormatting.RED), true);
            return false;
        }

        int commanded = 0;
        for (var minion : tamed) {
            if (minion.entity() instanceof Mob mob && mob.isAlive()) {
                mob.setTarget(livingTarget);
                commanded++;
            }
        }

        if (commanded > 0) {
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.4f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                livingTarget.getX(), livingTarget.getY() + livingTarget.getBbHeight(), livingTarget.getZ(),
                5, 0.3, 0.3, 0.3, 0.0);

            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.command_pack.success", commanded, target.getName())
                .withStyle(ChatFormatting.GREEN), true);
        }

        return commanded > 0;
    }

    private static Entity getTargetEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        double closestDist = range * range;
        Entity closest = null;

        for (Entity entity : player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity && e.isAlive())) {
            AABB entityBB = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> hitVec = entityBB.clip(eye, end);
            if (hitVec.isPresent()) {
                double dist = eye.distanceToSqr(hitVec.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }
}
