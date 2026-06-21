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
        MODIFY_HARVEST
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
     * Player UUID → list of active event powers.
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

    // ---- Server-side USE-key (right-click) state ----
    // The server has no native "is the use key held" signal for a tap right-click
    // on a plain/empty hand (player.isUsingItem() only covers item-use animations
    // like food/bow/shield). Apoli's key.use-bound active_self powers (e.g. the
    // deanos Mage spells) therefore never fired when polled from onTick. We bridge
    // that gap by recording the game tick of each right-click interaction event and
    // exposing it as a 1-tick-tolerant "pressed" query the onTick poll can read.
    private static final Map<UUID, Integer> LAST_USE_TICK = new ConcurrentHashMap<>();

    /** Record that the player pressed the USE (right-click) key this tick. */
    public static void recordUseKey(ServerPlayer player) {
        LAST_USE_TICK.put(player.getUUID(), player.tickCount);
    }

    // ---- Real (client-streamed) held-state for USE / ATTACK ----
    // The interaction-event stamp above only fires when there's something to
    // right-click; player.swinging only streams while actually mining/hitting.
    // Neither reflects "the key is physically held while pointing at nothing",
    // which is exactly how Apoli key.use / key.attack active_self powers are
    // meant to fire. The client reports the genuine key state via
    // VanillaKeyStatePayload (on edges) and we keep it here until the next change.
    private static final Set<UUID> USE_KEY_HELD = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ATTACK_KEY_HELD = ConcurrentHashMap.newKeySet();

    /** Apply a client-reported snapshot of the USE/ATTACK physical key state. */
    public static void setVanillaKeyState(ServerPlayer player, boolean useDown, boolean attackDown) {
        UUID id = player.getUUID();
        if (useDown) USE_KEY_HELD.add(id); else USE_KEY_HELD.remove(id);
        if (attackDown) ATTACK_KEY_HELD.add(id); else ATTACK_KEY_HELD.remove(id);
    }

    /**
     * True if the player right-clicked on this tick or the previous one. The
     * 1-tick tolerance absorbs the ordering skew between the interaction event
     * and the power onTick within the same game tick (either may run first).
     */
    public static boolean isUseKeyDown(ServerPlayer player) {
        if (USE_KEY_HELD.contains(player.getUUID())) return true;
        Integer t = LAST_USE_TICK.get(player.getUUID());
        return t != null && player.tickCount - t <= 1 && player.tickCount - t >= 0;
    }

    /** True if the ATTACK (left-click) key is physically held, per the client stream. */
    public static boolean isAttackKeyHeld(ServerPlayer player) {
        return ATTACK_KEY_HELD.contains(player.getUUID());
    }

    // ---- Server-side JUMP-key (airborne press) state ----
    // The server has no native "is the jump key pressed" signal. The old proxy for
    // key.jump-bound active_self powers approximated it as "airborne and moving
    // upward" (!onGround() && deltaMovement.y > 0), which fires during the natural
    // first jump's ascent and is FALSE at the moment a player re-presses jump while
    // falling — exactly the double-jump gesture. The client already sends an
    // AirJumpPayload on a real rising-edge jump press while airborne (and never on
    // the ground jump); NeoOriginsNetwork.handleAirJump records that tick here so
    // the onTick poll can read a genuine press.
    private static final Map<UUID, Integer> LAST_JUMP_TICK = new ConcurrentHashMap<>();

    /** Record that the player pressed JUMP while airborne this tick. */
    public static void recordJumpKey(ServerPlayer player) {
        LAST_JUMP_TICK.put(player.getUUID(), player.tickCount);
    }

    /**
     * True if the player pressed an airborne jump on this tick or the previous one.
     * Same 1-tick tolerance as {@link #isUseKeyDown} to absorb event/onTick ordering.
     */
    public static boolean isJumpKeyDown(ServerPlayer player) {
        Integer t = LAST_JUMP_TICK.get(player.getUUID());
        return t != null && player.tickCount - t <= 1 && player.tickCount - t >= 0;
    }

    /** Clear all tracked state (called on data reload). */
    public static void clearAll() {
        ACTIVE_POWERS.clear();
    }

    /** Remove a player entirely (called on disconnect). */
    public static void removePlayer(UUID playerId) {
        ACTIVE_POWERS.remove(playerId);
        LAST_USE_TICK.remove(playerId);
        LAST_JUMP_TICK.remove(playerId);
        USE_KEY_HELD.remove(playerId);
        ATTACK_KEY_HELD.remove(playerId);
    }
}
