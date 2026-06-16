package com.cyberday1.neoorigins.service;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks a brief post-pick invulnerability grace window for each player.
 *
 * <p>Granted only when a player finishes the <em>initial on-join</em> origin
 * pick (never on Orb-of-Origin re-picks). For a few seconds afterwards the
 * player should not take damage, so they don't eat a hit the instant they
 * spawn in — e.g. when a {@code spawn_location} origin teleports them straight
 * into a hostile area. Read by {@code CombatPowerEvents.onLivingDamage}.
 *
 * <p>Transient and non-persistent. Uses the monotonic server tick
 * ({@code level().getServer().getTickCount()}) as its clock
 * rather than level game-time, which {@code /time set} can move arbitrarily.
 * Entries self-prune in {@link #isActive(ServerPlayer)}; logout cleanup via
 * {@link #clear(UUID)} from {@code PlayerLifecycleEvents} is just hygiene.
 */
public final class FirstPickGraceTracker {

    private FirstPickGraceTracker() {}

    /** player UUID → server tick at which the grace window expires. */
    private static final Map<UUID, Integer> EXPIRY_TICK = new ConcurrentHashMap<>();

    /** Grant {@code ticks} of post-pick invulnerability grace from now. */
    public static void grant(ServerPlayer sp, int ticks) {
        EXPIRY_TICK.put(sp.getUUID(), sp.level().getServer().getTickCount() + ticks);
    }

    /**
     * True while the player's grace window is still open. Lazily removes the
     * entry once it has expired so the map self-prunes.
     */
    public static boolean isActive(ServerPlayer sp) {
        Integer expiry = EXPIRY_TICK.get(sp.getUUID());
        if (expiry == null) return false;
        if (sp.level().getServer().getTickCount() < expiry) return true;
        EXPIRY_TICK.remove(sp.getUUID());
        return false;
    }

    /** Drop any grace entry for the player (call on logout for hygiene). */
    public static void clear(UUID id) {
        EXPIRY_TICK.remove(id);
    }
}
