package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.condition.LocationCondition;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.power.builtin.ModifyPlayerSpawnPower;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Resolves and applies the {@code spawn_location} teleport for origins.
 *
 * <p>Invoked in three places:
 * <ul>
 *   <li>Right after a player selects an origin (picker or Orb) — moves them
 *       to the origin's spawn location if one is specified.</li>
 *   <li>On respawn when the player has no bed/respawn anchor — moves them
 *       to their primary origin's spawn location instead of world spawn.</li>
 *   <li>On respawn via {@code modify_player_spawn} powers, which take
 *       precedence over the above ({@link #applyRespawnSpawnOverrides}).</li>
 * </ul>
 *
 * <p>"Primary origin" for respawn purposes is the first origin (in sorted
 * layer order) that declares {@code spawn_location}.
 *
 * <p><b>Everything here is asynchronous.</b> Locating a biome-driven spawn can
 * cost millions of climate samples, so the search runs on a worker via
 * {@link AsyncSpawnLocator} and the teleport is applied on the server thread
 * when it lands. That means these methods return before the player has moved,
 * and cannot report whether a teleport happened — which is why
 * {@link #teleportToPrimaryOriginSpawn} returns {@code void}. Because the world
 * can change while a search runs, every callback re-validates the player and
 * their origin before touching anything.
 */
public final class OriginSpawnService {

    private OriginSpawnService() {}

    /**
     * Teleports the player to the given origin's {@code spawn_location}, if any.
     * No-op when the origin has no spec or no match can be located.
     *
     * <p>Asynchronous: returns immediately, teleports on a later tick.
     */
    public static void teleportToOriginSpawn(ServerPlayer player, Identifier originId) {
        Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
        if (origin == null || origin.spawnLocation().isEmpty()) return;
        if (!GameplayConfig.shouldApplySpawnLocation(originId)) return;
        teleportTo(player, origin.spawnLocation().get(), originId, NO_FOLLOW_UP);
    }

    /**
     * Finds the first origin on the player (in sorted layer order) that declares
     * a {@code spawn_location}, and teleports them there.
     *
     * <p>Asynchronous: returns immediately, teleports on a later tick. It used
     * to return whether a teleport happened; neither caller read that, and the
     * answer is no longer knowable synchronously.
     */
    public static void teleportToPrimaryOriginSpawn(ServerPlayer player) {
        teleportToPrimaryOriginSpawn(player, NO_FOLLOW_UP);
    }

    private static void teleportToPrimaryOriginSpawn(ServerPlayer player, Consumer<ServerPlayer> afterTeleport) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            Identifier originId = data.getOrigin(layer.id());
            if (originId == null) continue;
            Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null || origin.spawnLocation().isEmpty()) continue;
            if (!GameplayConfig.shouldApplySpawnLocation(originId)) continue;
            teleportTo(player, origin.spawnLocation().get(), originId, afterTeleport);
            return;
        }
    }

    /**
     * Respawn path: applies {@code modify_player_spawn} powers in power order,
     * first successful locate wins, and falls back to the player's primary
     * origin {@code spawn_location} if none of them resolved.
     *
     * <p>The original synchronous version tried each power in turn and stopped
     * at the first hit. That ordering is preserved here by collecting the
     * candidate specs up front (a cheap config read) and then walking them one
     * at a time: each miss chains to the next, and only after the last miss do
     * we fall back. Searches therefore never run concurrently for one player,
     * so a later power can never beat an earlier one to the teleport.
     *
     * <p>Call on the server thread, from the respawn event.
     */
    public static void applyRespawnSpawnOverrides(ServerPlayer player) {
        // Captured once: nothing in this flow sets a respawn position, so the
        // original per-iteration re-read was already constant. Capturing keeps
        // the gate answering the same question it did synchronously even though
        // the chain now spans ticks. (26.1 renamed getRespawnPosition() to
        // getRespawnConfig(); null still means "no bed / respawn anchor".)
        final boolean hasRespawnPosition = player.getRespawnConfig() != null;

        List<LocationCondition> specs = new ArrayList<>();
        ActiveOriginService.forEachOfType(player, ModifyPlayerSpawnPower.class, cfg -> {
            if (!cfg.overrideBed() && hasRespawnPosition) return;
            specs.add(cfg.location());
        });

        tryRespawnOverride(player, specs, 0, hasRespawnPosition);
    }

    private static void tryRespawnOverride(ServerPlayer player, List<LocationCondition> specs,
                                           int index, boolean hasRespawnPosition) {
        if (index >= specs.size()) {
            // Every modify_player_spawn power missed (or there were none):
            // fall back to the origin's own spawn_location, bed permitting.
            if (!hasRespawnPosition) teleportToPrimaryOriginSpawn(player, RECALL_PETS);
            return;
        }
        LocationCondition spec = specs.get(index);
        AsyncSpawnLocator.locate(player, spec, (live, target) -> {
            if (target.isPresent()) {
                apply(live, target.get());
                RECALL_PETS.accept(live);
                return;
            }
            tryRespawnOverride(live, specs, index + 1, hasRespawnPosition);
        });
    }

    private static final Consumer<ServerPlayer> NO_FOLLOW_UP = player -> {};

    /**
     * Re-runs the respawn pet recall after a deferred spawn teleport.
     *
     * <p>{@code PlayerLifecycleEvents.onPlayerRespawn} recalls tamed pets to the
     * tamer <i>after</i> the spawn overrides specifically so they arrive at the
     * player's final position. Now that a biome-driven spawn search lands a tick
     * or more later, that ordering no longer holds on its own — the pets would
     * be left at the respawn point while the player is teleported away. Recalling
     * again once the teleport actually lands restores it.
     * {@code TameMobPower.rewriteAI} is explicitly written to be re-runnable
     * (respawn recall, chunk-reload rehydrate), so the second pass is safe.
     */
    private static final Consumer<ServerPlayer> RECALL_PETS =
        com.cyberday1.neoorigins.power.builtin.TameMobPower::recallTamedOnRespawn;

    private static void teleportTo(ServerPlayer player, LocationCondition spec, Identifier originId,
                                   Consumer<ServerPlayer> afterTeleport) {
        // Dimension the player was in when the search was requested. The search
        // now spans ticks, so something else may deliberately relocate them
        // while it runs — the documented case is an origin whose
        // entity_action_chosen teleports the player into a pocket dimension
        // (Seer). Landing our teleport on top of that would yank them straight
        // back out, so a dimension change is treated as "someone else has
        // claimed this player" and we stand down.
        final var requestedIn = player.level().dimension();

        AsyncSpawnLocator.locate(player, spec, (live, target) -> {
            // Re-validate on arrival: the search ran across ticks, so the player
            // may have re-picked (Orb), or an operator may have flipped the
            // config kill switch, since it started.
            if (!GameplayConfig.shouldApplySpawnLocation(originId)) return;
            if (!stillHasOrigin(live, originId)) return;
            if (!live.level().dimension().equals(requestedIn)) return;
            if (target.isEmpty()) {
                NeoOrigins.LOGGER.warn(
                    "Could not locate spawn_location for origin {} on player {} — spec: {}",
                    originId, live.getName().getString(), spec.formatSummary());
                return;
            }
            apply(live, target.get());
            afterTeleport.accept(live);
        });
    }

    private static boolean stillHasOrigin(ServerPlayer player, Identifier originId) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        return data.getOrigins().containsValue(originId);
    }

    /** Applies a resolved target. Server thread only. */
    private static void apply(ServerPlayer player, LocationCondition.SpawnTarget target) {
        Vec3 pos = target.pos();
        if (target.level() == player.level()) {
            player.teleportTo(pos.x, pos.y, pos.z);
        } else {
            player.teleport(new TeleportTransition(
                target.level(), pos, Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                TeleportTransition.DO_NOTHING));
        }
    }
}
