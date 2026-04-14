package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Tracks which players have event-based compat powers active.
 * Populated by CompatPower onGranted/onRevoked callbacks.
 * Queried by CompatEventPowers during NeoForge events.
 *
 * All conditions are pre-compiled at load time to avoid per-event JSON parsing.
 */
public final class CompatPlayerState {

    private CompatPlayerState() {}

    public enum EventType {
        PREVENT_ITEM_USE,
        RESTRICT_ARMOR,
        PREVENT_SLEEP,
        PREVENT_BLOCK_USE,
        PREVENT_ENTITY_USE,
        MODIFY_FOOD
    }

    /**
     * Pre-compiled event power data. All predicates are resolved at load time,
     * never at event time.
     */
    public record EventPowerData(
        String powerId,
        EventType type,
        EntityCondition entityCondition,
        Predicate<ItemStack> itemPredicate,
        BiPredicate<ServerPlayer, BlockPos> blockPredicate,
        ArmorPredicate armorPredicate
    ) {
        /** Convenience constructors for common patterns. */
        public static EventPowerData withItemPredicate(String powerId, EventType type, Predicate<ItemStack> pred) {
            return new EventPowerData(powerId, type, null, pred, null, null);
        }

        public static EventPowerData withEntityCondition(String powerId, EventType type, EntityCondition cond) {
            return new EventPowerData(powerId, type, cond, null, null, null);
        }

        public static EventPowerData withBlockPredicate(String powerId, EventType type, BiPredicate<ServerPlayer, BlockPos> pred) {
            return new EventPowerData(powerId, type, null, null, pred, null);
        }

        public static EventPowerData withArmorPredicate(String powerId, ArmorPredicate pred) {
            return new EventPowerData(powerId, EventType.RESTRICT_ARMOR, null, null, null, pred);
        }

        public static EventPowerData noCondition(String powerId, EventType type) {
            return new EventPowerData(powerId, type, null, null, null, null);
        }
    }

    /** Predicate for armor restriction checks — takes the stack and the equipment slot. */
    @FunctionalInterface
    public interface ArmorPredicate {
        boolean isRestricted(ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot);
    }

    /**
     * Player UUID -> list of active event powers.
     * Uses ConcurrentHashMap for thread safety during tick/event overlap.
     */
    private static final Map<UUID, List<EventPowerData>> ACTIVE_POWERS = new ConcurrentHashMap<>();

    public static void register(ServerPlayer player, EventPowerData data) {
        ACTIVE_POWERS.computeIfAbsent(player.getUUID(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(data);
    }

    public static void unregister(ServerPlayer player, EventPowerData data) {
        var list = ACTIVE_POWERS.get(player.getUUID());
        if (list != null) {
            list.removeIf(d -> d.powerId().equals(data.powerId()));
            if (list.isEmpty()) ACTIVE_POWERS.remove(player.getUUID());
        }
    }

    /** Get all active event powers of a given type for a player. */
    public static List<EventPowerData> getPowers(ServerPlayer player, EventType type) {
        var list = ACTIVE_POWERS.get(player.getUUID());
        if (list == null) return List.of();
        List<EventPowerData> result = new ArrayList<>();
        synchronized (list) {
            for (EventPowerData d : list) {
                if (d.type() == type) result.add(d);
            }
        }
        return result;
    }

    /** Check if a player has any event power of the given type. */
    public static boolean hasPower(ServerPlayer player, EventType type) {
        var list = ACTIVE_POWERS.get(player.getUUID());
        if (list == null) return false;
        synchronized (list) {
            for (EventPowerData d : list) {
                if (d.type() == type) return true;
            }
        }
        return false;
    }

    /** Clear all tracked state (called on data reload). */
    public static void clearAll() {
        ACTIVE_POWERS.clear();
    }

    /** Remove a player entirely (called on disconnect). */
    public static void removePlayer(UUID playerId) {
        ACTIVE_POWERS.remove(playerId);
    }
}
