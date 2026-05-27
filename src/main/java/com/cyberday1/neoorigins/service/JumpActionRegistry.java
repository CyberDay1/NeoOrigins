package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-player jump-action registry. {@code origins:modify_jump} powers may
 * declare an {@code entity_action} that fires every time the player jumps;
 * those actions are registered here at power-grant time and fired by
 * {@link com.cyberday1.neoorigins.event.JumpEventHandler} from
 * {@code LivingJumpEvent}.
 *
 * <p>Multiple powers can register for the same player. Each entry is keyed
 * by the power id so revoke/regrant cycles don't leak duplicates.
 *
 * <p>An optional {@link EntityCondition} may be supplied at registration;
 * the action only fires when the condition tests true. A null condition
 * means always-fire.
 */
public final class JumpActionRegistry {

    private JumpActionRegistry() {}

    public record Entry(String powerId, EntityAction action, EntityCondition condition) {}

    private static final Map<UUID, List<Entry>> REGISTRY = new ConcurrentHashMap<>();

    public static void register(ServerPlayer player, String powerId, EntityAction action, EntityCondition condition) {
        if (action == null || action == EntityAction.NOOP) return;
        REGISTRY.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>())
            .add(new Entry(powerId, action, condition));
    }

    public static void unregister(ServerPlayer player, String powerId) {
        List<Entry> entries = REGISTRY.get(player.getUUID());
        if (entries == null) return;
        entries.removeIf(e -> e.powerId.equals(powerId));
        if (entries.isEmpty()) REGISTRY.remove(player.getUUID());
    }

    public static void fire(ServerPlayer player) {
        List<Entry> entries = REGISTRY.get(player.getUUID());
        if (entries == null || entries.isEmpty()) return;
        for (Entry e : entries) {
            if (e.condition != null && !e.condition.test(player)) continue;
            e.action.execute(player);
        }
    }

    public static void clearPlayer(UUID uuid) {
        REGISTRY.remove(uuid);
    }
}
