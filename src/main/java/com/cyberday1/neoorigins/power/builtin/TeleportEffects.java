package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Shared teleport VFX — enderman-style sound + portal particles at both
 * the departure and arrival positions.
 */
final class TeleportEffects {
    private TeleportEffects() {}

    /** Play enderman teleport sound + particles at the given position in the player's current level. */
    static void playAt(ServerPlayer player, Vec3 pos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        playAt(level, pos);
    }

    /** Play enderman teleport sound + particles at the given position in a specific level. */
    static void playAt(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL,
            pos.x, pos.y + 1.0, pos.z, 32, 0.3, 0.5, 0.3, 0.5);
    }

    /** Play departure at player's current position, teleport (same level), then play arrival. */
    static void teleportWithEffects(ServerPlayer player, double x, double y, double z) {
        if (!(player.level() instanceof ServerLevel level)) return;
        teleportWithEffects(player, level, x, y, z);
    }

    /**
     * Play departure at the player's current position/level, teleport to {@code targetLevel}
     * (routes through a cross-dimension TeleportTransition when it differs from the current
     * level), then play arrival in the DESTINATION level.
     */
    static void teleportWithEffects(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z) {
        playAt(player, player.position());
        if (targetLevel == player.level()) {
            player.teleportTo(x, y, z);
        } else {
            player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
                targetLevel, new Vec3(x, y, z), Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        }
        playAt(targetLevel, new Vec3(x, y, z));
    }
}
