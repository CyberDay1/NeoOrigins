package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks summoned minions per player. Handles despawn timers and
 * notifies the summoner when a minion dies in combat.
 */
public final class MinionTracker {

    private MinionTracker() {}

    public record TrackedMinion(LivingEntity entity, String mobType, int spawnTick, int despawnTick, float deathDamage) {}

    /** Player UUID → list of tracked minions. */
    private static final Map<UUID, List<TrackedMinion>> MINIONS = new ConcurrentHashMap<>();

    /** Register a newly summoned minion. */
    public static void track(ServerPlayer summoner, LivingEntity minion, String mobType,
                             int currentTick, int despawnTicks, float deathDamage) {
        MINIONS.computeIfAbsent(summoner.getUUID(), k -> new ArrayList<>())
            .add(new TrackedMinion(minion, mobType, currentTick, currentTick + despawnTicks, deathDamage));
    }

    /** Count living minions of a given mob type for a player. */
    public static int countAlive(UUID playerUuid, String mobType) {
        List<TrackedMinion> list = MINIONS.get(playerUuid);
        if (list == null) return 0;
        return (int) list.stream()
            .filter(m -> m.mobType().equals(mobType) && m.entity().isAlive())
            .count();
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
            if (!m.entity().isAlive()) {
                it.remove();
                continue;
            }
            if (currentTick >= m.despawnTick()) {
                m.entity().discard();
                it.remove();
            }
        }
    }

    /**
     * Called when any LivingEntity dies. If it's a tracked minion that died
     * from combat (not discarded/despawned), damage the summoner.
     */
    public static void onEntityDeath(LivingEntity entity) {
        for (var entry : MINIONS.entrySet()) {
            Iterator<TrackedMinion> it = entry.getValue().iterator();
            while (it.hasNext()) {
                TrackedMinion m = it.next();
                if (m.entity() == entity) {
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

    /** Clean up all minions for a player (e.g., on logout or origin change). */
    public static void clearAll(UUID playerUuid) {
        List<TrackedMinion> list = MINIONS.remove(playerUuid);
        if (list != null) {
            for (TrackedMinion m : list) {
                if (m.entity().isAlive()) m.entity().discard();
            }
        }
    }
}
