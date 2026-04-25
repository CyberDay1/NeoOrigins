package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Resolves and applies the {@code spawn_location} teleport for origins.
 *
 * <p>Invoked in two places:
 * <ul>
 *   <li>Right after a player selects an origin (picker or Orb) — moves them
 *       to the origin's spawn location if one is specified.</li>
 *   <li>On respawn when the player has no bed/respawn anchor — moves them
 *       to their primary origin's spawn location instead of world spawn.</li>
 * </ul>
 *
 * <p>"Primary origin" for respawn purposes is the first origin (in sorted
 * layer order) that declares {@code spawn_location}.
 */
public final class OriginSpawnService {

    private OriginSpawnService() {}

    /**
     * Teleports the player to the given origin's {@code spawn_location}, if any.
     * No-op when the origin has no spec or no match can be located.
     */
    public static void teleportToOriginSpawn(ServerPlayer player, ResourceLocation originId) {
        Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
        if (origin == null || origin.spawnLocation().isEmpty()) return;
        if (!NeoOriginsConfig.shouldApplySpawnLocation(originId)) return;
        teleportTo(player, origin.spawnLocation().get(), originId);
    }

    /**
     * Finds the first origin on the player (in sorted layer order) that declares
     * a {@code spawn_location}, and teleports them there. Returns true if a
     * teleport was performed.
     */
    public static boolean teleportToPrimaryOriginSpawn(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            ResourceLocation originId = data.getOrigin(layer.id());
            if (originId == null) continue;
            Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null || origin.spawnLocation().isEmpty()) continue;
            if (!NeoOriginsConfig.shouldApplySpawnLocation(originId)) continue;
            teleportTo(player, origin.spawnLocation().get(), originId);
            return true;
        }
        return false;
    }

    private static void teleportTo(ServerPlayer player, LocationCondition spec, ResourceLocation originId) {
        Optional<LocationCondition.SpawnTarget> target = spec.locateSpawn(player);
        if (target.isEmpty()) {
            NeoOrigins.LOGGER.warn(
                "Could not locate spawn_location for origin {} on player {} — check dimension/biome/structure filter.",
                originId, player.getName().getString());
            return;
        }
        LocationCondition.SpawnTarget t = target.get();
        Vec3 pos = t.pos();
        if (t.level() == player.serverLevel()) {
            player.teleportTo(pos.x, pos.y, pos.z);
        } else {
            player.changeDimension(new DimensionTransition(
                t.level(), pos, Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
        }
    }
}
