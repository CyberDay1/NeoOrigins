package com.cyberday1.neoorigins.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lightweight tick-delayed action scheduler for Route B powers.
 * Entries are drained in PlayerLifecycleEvents.onPlayerTick.
 */
public final class CompatTickScheduler {

    private CompatTickScheduler() {}

    private record ScheduledAction(long tickTarget, Consumer<ServerPlayer> action) {}

    /** Player UUID → list of scheduled actions. */
    private static final Map<UUID, Deque<ScheduledAction>> QUEUES = new ConcurrentHashMap<>();

    public static void schedule(long tickTarget, ServerPlayer player, Consumer<ServerPlayer> action) {
        QUEUES.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>())
            .add(new ScheduledAction(tickTarget, action));
    }

    /** Called from PlayerLifecycleEvents.onPlayerTick. Drains expired entries for this player only. */
    public static void tick(ServerPlayer player) {
        Deque<ScheduledAction> queue = QUEUES.get(player.getUUID());
        if (queue == null || queue.isEmpty()) return;
        long currentTick = player.level().getServer() == null ? 0 : player.level().getServer().getTickCount();
        queue.removeIf(sa -> {
            if (sa.tickTarget() <= currentTick) {
                try {
                    sa.action().accept(player);
                } catch (Exception e) {
                    com.cyberday1.neoorigins.NeoOrigins.LOGGER.warn(
                        "[scheduler] scheduled action failed for {}: {}",
                        player.getName().getString(), e.toString());
                }
                return true;
            }
            return false;
        });
    }

    /** Clean up all scheduled actions for a player (e.g., on logout). */
    public static void clearPlayer(UUID playerUuid) {
        QUEUES.remove(playerUuid);
    }
}
