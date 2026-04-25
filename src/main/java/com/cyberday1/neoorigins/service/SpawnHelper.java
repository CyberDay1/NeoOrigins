package com.cyberday1.neoorigins.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Cross-version wrapper for setting a player's respawn point.
 *
 * <p>26.1 refactored {@code ServerPlayer.setRespawnPosition} to take a
 * {@code ServerPlayer.RespawnConfig} struct; 1.21.1 still uses the older
 * {@code (dimension, pos, angle, forced, sendMessage)} 5-arg form. Powers
 * that need to write a respawn point — like {@code PreventActionPower}
 * {@code SLEEP} (phantom, vampire) setting a bed anchor without actually
 * sleeping — should call through this helper.
 *
 * <p>1.21.1-specific implementation using the pre-RespawnConfig signature.
 */
public final class SpawnHelper {

    private SpawnHelper() {}

    /**
     * Writes the player's respawn point to the given dimension + block pos,
     * mirroring the behavior of a successful bed sleep. Intended for
     * "can set spawn without sleeping" semantics.
     *
     * @param player       target player
     * @param dimension    dimension key (usually {@code player.level().dimension()})
     * @param pos          respawn block position (usually the bed pos)
     * @param angle        respawn facing angle (usually {@code player.getYRot()})
     * @param forced       if true, marks the spawn as forced (bed-quality,
     *                     survives bed destruction)
     * @param sendMessage  if true, sends the "Spawn point set" actionbar
     */
    public static void setBedSpawn(ServerPlayer player,
                                   ResourceKey<Level> dimension,
                                   BlockPos pos,
                                   float angle,
                                   boolean forced,
                                   boolean sendMessage) {
        player.setRespawnPosition(dimension, pos, angle, forced, sendMessage);
    }
}
