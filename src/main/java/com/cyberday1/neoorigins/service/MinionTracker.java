package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks summoned minions per player. Handles despawn timers and
 * notifies the summoner when a minion dies in combat.
 *
 * <p>Minions are tracked by UUID rather than by Java reference so that
 * entries survive dimension changes — when a minion walks through a portal,
 * vanilla creates a new {@link Entity} instance in the target dimension but
 * preserves the UUID. Storing the old reference made {@code isAlive()}
 * return false and the tracker silently forget the minion. Resolving by
 * UUID via {@link MinecraftServer#getAllLevels()} picks up the new instance
 * in whichever dimension it's currently loaded in.
 */
public final class MinionTracker {

    private MinionTracker() {}

    public record TrackedMinion(UUID minionUuid, String mobType, int spawnTick, int despawnTick, float deathDamage) {
        /**
         * Resolves this minion's current entity by scanning all loaded server
         * dimensions for its UUID. Returns null if the minion is unloaded,
         * despawned, or dead.
         */
        public LivingEntity entity() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(minionUuid);
                if (e instanceof LivingEntity le && !le.isRemoved()) return le;
            }
            return null;
        }
    }

    /** Player UUID → list of tracked minions. */
    private static final Map<UUID, List<TrackedMinion>> MINIONS = new ConcurrentHashMap<>();

    /** Register a newly summoned minion. */
    public static void track(ServerPlayer summoner, LivingEntity minion, String mobType,
                             int currentTick, int despawnTicks, float deathDamage) {
        MINIONS.computeIfAbsent(summoner.getUUID(), k -> new ArrayList<>())
            .add(new TrackedMinion(minion.getUUID(), mobType, currentTick, currentTick + despawnTicks, deathDamage));
    }

    /** Count living minions of a given mob type for a player. */
    public static int countAlive(UUID playerUuid, String mobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return 0;
        int count = 0;
        for (TrackedMinion m : list) {
            if (!m.mobType().equals(mobType)) continue;
            LivingEntity entity = m.entity();
            if (entity != null && entity.isAlive()) count++;
        }
        return count;
    }

    /**
     * Called every server tick per player. Despawns expired minions and
     * removes dead entries.
     */
    public static void tick(ServerPlayer player) {
        List<TrackedMinion> list = MINIONS.get(player.getUUID());
        if (list == null || list.isEmpty()) return;

        int currentTick = player.tickCount;
        Iterator<TrackedMinion> it = list.iterator();
        while (it.hasNext()) {
            TrackedMinion m = it.next();
            LivingEntity entity = m.entity();

            // Entity not resolvable (in an unloaded chunk, or already gone).
            // Keep the entry until despawn time so we don't evict minions that
            // are just temporarily unloaded.
            if (entity == null) {
                if (currentTick >= m.despawnTick()) it.remove();
                continue;
            }

            if (!entity.isAlive()) {
                it.remove();
                continue;
            }
            if (currentTick >= m.despawnTick()) {
                entity.discard();
                it.remove();
            }
        }
    }

    /**
     * Called when any LivingEntity dies. If it's a tracked minion that died
     * from combat (not discarded/despawned), damage the summoner.
     */
    public static void onEntityDeath(LivingEntity entity) {
        UUID entityUuid = entity.getUUID();
        for (var entry : MINIONS.entrySet()) {
            Iterator<TrackedMinion> it = entry.getValue().iterator();
            while (it.hasNext()) {
                TrackedMinion m = it.next();
                if (m.minionUuid().equals(entityUuid)) {
                    it.remove();
                    // Damage the summoner — the entity died in combat, not from discard
                    ServerPlayer summoner = entity.level().getServer() != null
                        ? entity.level().getServer().getPlayerList().getPlayer(entry.getKey())
                        : null;
                    if (summoner != null && m.deathDamage() > 0) {
                        summoner.hurt(summoner.damageSources().magic(), m.deathDamage());
                        NeoOrigins.LOGGER.debug("Necromancer {} took {} backlash damage from minion death",
                            summoner.getName().getString(), m.deathDamage());
                    }
                    return;
                }
            }
        }
    }

    /**
     * Reverse lookup: given an entity, return the ServerPlayer that summoned it
     * if it is a currently-tracked minion. Iterates the full tracker map;
     * acceptable because tracked-minion counts stay small (per-player caps
     * enforced by the power configs).
     */
    public static Optional<ServerPlayer> summonerOf(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        UUID entityUuid = entity.getUUID();
        for (var entry : MINIONS.entrySet()) {
            for (TrackedMinion m : entry.getValue()) {
                if (m.minionUuid().equals(entityUuid)) {
                    return Optional.ofNullable(server.getPlayerList().getPlayer(entry.getKey()));
                }
            }
        }
        return Optional.empty();
    }

    /** Get all living tracked minions of a given mob type for a player. */
    public static List<TrackedMinion> getAlive(UUID playerUuid, String mobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return List.of();
        List<TrackedMinion> alive = new ArrayList<>();
        for (TrackedMinion m : list) {
            if (!m.mobType().equals(mobType)) continue;
            LivingEntity entity = m.entity();
            if (entity != null && entity.isAlive()) alive.add(m);
        }
        return alive;
    }

    /** Clean up all minions for a player (e.g., on logout or origin change). */
    public static void clearAll(UUID playerUuid) {
        List<TrackedMinion> list = MINIONS.remove(playerUuid);
        if (list != null) {
            for (TrackedMinion m : list) {
                LivingEntity entity = m.entity();
                if (entity != null && entity.isAlive()) entity.discard();
            }
        }
    }
}
