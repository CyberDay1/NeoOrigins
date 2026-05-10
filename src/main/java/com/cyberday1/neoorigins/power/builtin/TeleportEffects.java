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

    /** Play enderman teleport sound + particles at the given position. */
    static void playAt(ServerPlayer player, Vec3 pos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL,
            pos.x, pos.y + 1.0, pos.z, 32, 0.3, 0.5, 0.3, 0.5);
    }

    /** Play departure at player's current position, teleport, then play arrival. */
    static void teleportWithEffects(ServerPlayer player, double x, double y, double z) {
        playAt(player, player.position());
        player.teleportTo(x, y, z);
        playAt(player, new Vec3(x, y, z));
    }
}
