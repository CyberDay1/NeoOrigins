package com.cyberday1.neoorigins.compat;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * Lightweight tick-delayed action scheduler for Route B powers.
 * Entries are drained in OriginEventHandler.onPlayerTick.
 */
public final class CompatTickScheduler {

    private CompatTickScheduler() {}

    private record ScheduledAction(long tickTarget, UUID playerId, Consumer<ServerPlayer> action) {}

    private static final ConcurrentLinkedDeque<ScheduledAction> QUEUE = new ConcurrentLinkedDeque<>();

    public static void schedule(long tickTarget, ServerPlayer player, Consumer<ServerPlayer> action) {
        QUEUE.add(new ScheduledAction(tickTarget, player.getUUID(), action));
    }

    /** Called from OriginEventHandler.onPlayerTick. Drains and runs expired entries for this player. */
    public static void tick(ServerPlayer player) {
        if (QUEUE.isEmpty()) return;
        long currentTick = player.level().getServer() == null ? 0 : player.level().getServer().getTickCount();
        UUID id = player.getUUID();
        QUEUE.removeIf(sa -> {
            if (sa.tickTarget() <= currentTick && sa.playerId().equals(id)) {
                try { sa.action().accept(player); } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }
}
