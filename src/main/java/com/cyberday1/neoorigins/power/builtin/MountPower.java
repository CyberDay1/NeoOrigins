package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.service.MountConsentManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Active power that lets the player mount (ride) any living entity.
 *
 * <p>Mobs are mounted immediately. Players require consent according
 * to the server's configured {@link com.cyberday1.neoorigins.NeoOriginsConfig.ConsentMode}.
 *
 * <p>Pressing the keybind while already riding dismounts the player.
 */
public class MountPower extends AbstractActivePower<MountPower.Config> {

    public record Config(
        double range,
        int cooldownTicks,
        int hungerCost,
        boolean allowPlayers,
        boolean allowMobs,
        boolean blockBosses,
        String mountPosition,
        String type
    ) implements AbstractActivePower.Config {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("range", 5.0).forGetter(Config::range),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(Config::cooldownTicks),
            Codec.INT.optionalFieldOf("hunger_cost", 0).forGetter(Config::hungerCost),
            Codec.BOOL.optionalFieldOf("allow_players", true).forGetter(Config::allowPlayers),
            Codec.BOOL.optionalFieldOf("allow_mobs", true).forGetter(Config::allowMobs),
            Codec.BOOL.optionalFieldOf("block_bosses", true).forGetter(Config::blockBosses),
            Codec.STRING.optionalFieldOf("mount_position", "centered").forGetter(Config::mountPosition),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        // Toggle off: if already riding, dismount
        if (player.isPassenger()) {
            player.stopRiding();
            player.setData(EntityAttachments.mountPosition(), "");
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.dismounted").withStyle(ChatFormatting.GRAY), true);
            return true;
        }

        // Raycast for target
        Entity target = getTargetEntity(player, config.range());
        if (target == null) {
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.no_target").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        // Player target
        if (target instanceof ServerPlayer targetPlayer) {
            if (!config.allowPlayers()) {
                player.sendSystemMessage(Component.translatable(
                    "power.neoorigins.mount.not_allowed_players").withStyle(ChatFormatting.RED), true);
                return false;
            }

            boolean mounted = MountConsentManager.tryMount(player, targetPlayer, config.mountPosition());
            // Consume cooldown regardless — prevents spamming requests
            return true;
        }

        // Mob target
        if (target instanceof LivingEntity living) {
            if (!config.allowMobs()) {
                player.sendSystemMessage(Component.translatable(
                    "power.neoorigins.mount.not_allowed_mobs").withStyle(ChatFormatting.RED), true);
                return false;
            }

            // Boss check: boss mobs cannot use portals
            if (config.blockBosses() && !living.canUsePortal(false)) {
                player.sendSystemMessage(Component.translatable(
                    "power.neoorigins.mount.boss_blocked").withStyle(ChatFormatting.RED), true);
                return false;
            }

            // Already has a rider
            if (!target.getPassengers().isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                    "power.neoorigins.mount.already_ridden").withStyle(ChatFormatting.RED), true);
                return false;
            }

            player.startRiding(target, true);
            player.setData(EntityAttachments.mountPosition(), config.mountPosition());
            player.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.success", target.getName())
                .withStyle(ChatFormatting.GREEN), true);
            return true;
        }

        return false;
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        if (player.isPassenger()) {
            player.stopRiding();
            player.setData(EntityAttachments.mountPosition(), "");
        }
    }

    private static Entity getTargetEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        double closestDist = range * range;
        Entity closest = null;

        for (Entity entity : player.level().getEntities(player, searchBox,
                e -> e instanceof LivingEntity && e.isAlive())) {
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
