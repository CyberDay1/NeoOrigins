package com.cyberday1.neoorigins.service;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last tick at which each player took damage, so powers can
 * gate on "out of combat" — e.g. explorer campfire regen that should
 * pause mid-fight.
 *
 * <p>Updated by {@code CombatPowerEvents.onLivingDamage} whenever the
 * victim is a ServerPlayer. Read by the {@code neoorigins:out_of_combat}
 * condition. Cleared on logout via {@code PlayerLifecycleEvents}.
 */
public final class CombatTracker {

    private CombatTracker() {}

    private static final Map<UUID, Integer> LAST_DAMAGED_TICK = new ConcurrentHashMap<>();

    /** Record that the player took damage at their current tickCount. */
    public static void markDamaged(ServerPlayer player) {
        LAST_DAMAGED_TICK.put(player.getUUID(), player.tickCount);
    }

    /**
     * Ticks since the player last took damage. Returns
     * {@link Integer#MAX_VALUE} if the player has never been damaged
     * this session (so out-of-combat checks trivially pass at spawn).
     */
    public static int ticksSinceLastDamage(ServerPlayer player) {
        Integer last = LAST_DAMAGED_TICK.get(player.getUUID());
        if (last == null) return Integer.MAX_VALUE;
        int delta = player.tickCount - last;
        return delta < 0 ? 0 : delta;
    }

    /** Call on player logout to free the map entry. */
    public static void forget(UUID playerId) {
        LAST_DAMAGED_TICK.remove(playerId);
    }
}
