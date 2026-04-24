package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.EntityAttachments.MinionOwner;
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
         * Resolves this minion's current entity. Checks the last known dimension
         * first (stamped at track-time or on previous successful resolve); falls
         * back to scanning all loaded dimensions only if the hint misses, which
         * happens on dimension-change or server restart. The hint is updated
         * when the scan locates the minion in a different dimension so
         * subsequent calls stay fast.
         */
        public LivingEntity entity() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;

            var hintKey = DIM_HINTS.get(minionUuid);
            if (hintKey != null) {
                ServerLevel hintLevel = server.getLevel(hintKey);
                if (hintLevel != null) {
                    Entity e = hintLevel.getEntity(minionUuid);
                    if (e instanceof LivingEntity le && !le.isRemoved()) return le;
                }
            }

            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().equals(hintKey)) continue;
                Entity e = level.getEntity(minionUuid);
                if (e instanceof LivingEntity le && !le.isRemoved()) {
                    DIM_HINTS.put(minionUuid, level.dimension());
                    return le;
                }
            }
            return null;
        }
    }

    /** Player UUID → list of tracked minions. */
    private static final Map<UUID, List<TrackedMinion>> MINIONS = new ConcurrentHashMap<>();

    /** Minion UUID → last-known dimension. Used by {@link TrackedMinion#entity()}
     *  to skip the N-dimension scan. Stamped on {@link #track(ServerPlayer, LivingEntity, String, int, int, float)}
     *  and refreshed whenever a scan finds the minion in a different dimension. */
    private static final Map<UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> DIM_HINTS =
        new ConcurrentHashMap<>();

    /** Register a newly summoned minion. Also stamps the mob with a persistent
     * {@code minion_owner} attachment so ownership survives dimension changes
     * and server restarts (the in-memory {@link #MINIONS} map is session-scoped). */
    public static void track(ServerPlayer summoner, LivingEntity minion, String mobType,
                             int currentTick, int despawnTicks, float deathDamage) {
        MINIONS.computeIfAbsent(summoner.getUUID(), k -> new ArrayList<>())
            .add(new TrackedMinion(minion.getUUID(), mobType, currentTick, currentTick + despawnTicks, deathDamage));
        minion.setData(EntityAttachments.minionOwner(), MinionOwner.of(summoner.getUUID()));
        if (minion.level() instanceof ServerLevel sl) {
            DIM_HINTS.put(minion.getUUID(), sl.dimension());
        }
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
                if (currentTick >= m.despawnTick()) {
                    it.remove();
                    DIM_HINTS.remove(m.minionUuid());
                }
                continue;
            }

            if (!entity.isAlive()) {
                it.remove();
                DIM_HINTS.remove(m.minionUuid());
                continue;
            }
            if (currentTick >= m.despawnTick()) {
                entity.discard();
                it.remove();
                DIM_HINTS.remove(m.minionUuid());
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
                    DIM_HINTS.remove(entityUuid);
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
     * True if {@code entity} is currently tracked as a minion summoned by the
     * player identified by {@code summonerUuid}. Used by the targeting
     * interceptor to stop summoned mobs from attacking their own summoner.
     *
     * <p>Checks the persistent {@code minion_owner} attachment first (survives
     * dimension changes + server restarts), then falls back to the in-memory
     * map so entries from older saves without the attachment still resolve
     * during the current session.
     */
    public static boolean isTrackedMinionOf(Entity entity, UUID summonerUuid) {
        if (entity == null) return false;
        MinionOwner owner = entity.getData(EntityAttachments.minionOwner());
        if (owner.isOwnedBy(summonerUuid)) return true;
        List<TrackedMinion> list = MINIONS.get(summonerUuid);
        if (list == null) return false;
        UUID entityUuid = entity.getUUID();
        for (TrackedMinion m : list) {
            if (m.minionUuid().equals(entityUuid)) return true;
        }
        return false;
    }

    /**
     * Reverse lookup: return the ServerPlayer who summoned {@code entity}
     * if it is a currently-tracked minion. Checks the persistent
     * {@code minion_owner} attachment first (O(1) per-entity), then falls
     * back to scanning the in-memory map for session-only entries.
     *
     * @return the online summoner, or empty if the entity is unsummoned
     *         or the summoner is offline.
     */
    public static Optional<ServerPlayer> summonerOf(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();

        MinionOwner owner = entity.getData(EntityAttachments.minionOwner());
        if (owner.ownerUuid().isPresent()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(owner.ownerUuid().get());
            if (sp != null) return Optional.of(sp);
        }

        UUID entityUuid = entity.getUUID();
        for (var entry : MINIONS.entrySet()) {
            for (TrackedMinion m : entry.getValue()) {
                if (m.minionUuid().equals(entityUuid)) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
                    return Optional.ofNullable(sp);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * True if {@code entity} is a summoned minion for any player. Used by the
     * loot-drop interceptor — summoned mobs should never leave gear/items behind
     * because the summoner effectively conjured them (and their equipment) for
     * free. Checks the persistent attachment first, then the in-memory map.
     */
    public static boolean isAnyTrackedMinion(Entity entity) {
        if (entity == null) return false;
        if (entity.getData(EntityAttachments.minionOwner()).isOwned()) return true;
        UUID entityUuid = entity.getUUID();
        for (List<TrackedMinion> list : MINIONS.values()) {
            for (TrackedMinion m : list) {
                if (m.minionUuid().equals(entityUuid)) return true;
            }
        }
        return false;
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
                DIM_HINTS.remove(m.minionUuid());
            }
        }
    }
}
