package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * O(1) event → handler dispatch for 2.0's generic event-triggered powers.
 *
 * <p>Replaces {@link ActiveOriginService#forEachOfType(ServerPlayer, Class, java.util.function.Consumer)}
 * linear scans for event-carrying powers. Generic event-triggered power types
 * register their compiled handler functions here at {@code onGranted} time and
 * remove them at {@code onRevoked} time. Dispatch sites (hit, kill, block-break,
 * damage-taken, etc.) then iterate the small per-player list for the specific
 * event.
 *
 * <p>Thread-safety: all mutations and lookups happen on the server thread.
 * Backing structures use concurrent maps purely for safe iteration under
 * incidental cross-thread access (logout, serialisation).
 */
public final class EventPowerIndex {

    private EventPowerIndex() {}

    /** Event keys used by generic event-triggered powers. */
    public enum Event {
        // ----- core lifecycle / combat -----
        ATTACK,                 // player attacked something
        HIT_TAKEN,              // player took damage
        KILL,                   // player killed a living entity
        DEATH,                  // player died
        BLOCK_BREAK,
        BLOCK_PLACE,
        ITEM_USE,
        RESPAWN,
        TICK,
        DIMENSION_CHANGE,
        CLIMB,
        JUMP,
        PROJECTILE_HIT,         // projectile owned by the player hit something

        // ----- Origins-Classes hooks: actions -----
        CRAFT_ITEM,             // item crafted in a crafting table / inventory
        SMELT_ITEM,             // item removed from furnace / smoker output
        ENCHANT_ITEM,           // enchantment applied at enchanting table
        ANVIL_REPAIR,           // anvil repair / combine
        BONEMEAL,               // bonemeal applied
        BREED,                  // breeding produced a baby
        TAME,                   // entity was tamed
        FOOD_EATEN,             // player finished eating an edible
        ADVANCEMENT_EARNED,
        TRADE_COMPLETED,        // villager trade finished
        VILLAGER_INTERACT,      // right-click villager

        // ----- Origins-Classes hooks: modifiers (return a float) -----
        MOD_EXHAUSTION,         // hunger drain multiplier
        MOD_BREAK_SPEED,        // block break speed multiplier
        MOD_NATURAL_REGEN,      // natural heal amount multiplier
        MOD_XP_GAIN,            // xp orb value multiplier
        MOD_TRADE_PRICE,        // villager trade cost multiplier
        MOD_CRAFT_AMOUNT,       // crafting output count multiplier
        MOD_ENCHANT_LEVEL,      // enchanting table level multiplier / bonus
        MOD_HARVEST_DROPS,      // extra drops multiplier on break/drops
        MOD_TELEPORT_RANGE,     // ender pearl / teleport distance
        MOD_FALL_DAMAGE,        // fall damage multiplier
        MOD_KNOCKBACK,          // incoming knockback strength multiplier
        MOD_POTION_DURATION,    // added-effect duration multiplier
        MOD_ANVIL_COST,         // anvil repair / combine XP cost multiplier
        MOD_CRAFTED_FOOD_SATURATION, // additive saturation bonus for crafted food
        MOD_BONEMEAL_EXTRA,     // additive extra bonemeal applications
    }

    /** A compiled handler: accepts the triggering player and an event-specific context object. */
    @FunctionalInterface
    public interface Handler extends BiConsumer<ServerPlayer, Object> {}

    /**
     * Modifier handler: receives (player, context, current value) and returns the modified value.
     * Multiple modifiers on the same event chain in registration order.
     */
    @FunctionalInterface
    public interface ModifierHandler {
        float apply(ServerPlayer player, Object context, float base);
    }

    /** Token returned by register(); pass back to unregister(). */
    public static final class Token {
        final UUID uuid;
        final Event event;
        final Handler handler;          // non-null for action handlers
        final ModifierHandler modifier; // non-null for modifier handlers
        Token(UUID uuid, Event event, Handler handler, ModifierHandler modifier) {
            this.uuid = uuid;
            this.event = event;
            this.handler = handler;
            this.modifier = modifier;
        }
    }

    private static final Map<UUID, Map<Event, List<Handler>>> INDEX = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Event, List<ModifierHandler>>> MOD_INDEX = new ConcurrentHashMap<>();

    /** Register a handler for a player+event. Typically called in {@code PowerType.onGranted}. */
    public static Token register(ServerPlayer player, Event event, Handler handler) {
        UUID uuid = player.getUUID();
        INDEX.computeIfAbsent(uuid, k -> new EnumMap<>(Event.class))
             .computeIfAbsent(event, k -> new ArrayList<>())
             .add(handler);
        return new Token(uuid, event, handler, null);
    }

    /** Register a modifier handler (for MOD_* events that chain a float value). */
    public static Token registerModifier(ServerPlayer player, Event event, ModifierHandler modifier) {
        UUID uuid = player.getUUID();
        MOD_INDEX.computeIfAbsent(uuid, k -> new EnumMap<>(Event.class))
             .computeIfAbsent(event, k -> new ArrayList<>())
             .add(modifier);
        return new Token(uuid, event, null, modifier);
    }

    /** Remove a handler previously registered via {@link #register} or {@link #registerModifier}. */
    public static void unregister(Token token) {
        if (token == null) return;
        if (token.handler != null) {
            Map<Event, List<Handler>> perEvent = INDEX.get(token.uuid);
            if (perEvent != null) {
                List<Handler> list = perEvent.get(token.event);
                if (list != null) list.remove(token.handler);
            }
        }
        if (token.modifier != null) {
            Map<Event, List<ModifierHandler>> perEvent = MOD_INDEX.get(token.uuid);
            if (perEvent != null) {
                List<ModifierHandler> list = perEvent.get(token.event);
                if (list != null) list.remove(token.modifier);
            }
        }
    }

    /** Dispatch an event to all registered handlers. */
    public static void dispatch(ServerPlayer player, Event event, Object context) {
        Map<Event, List<Handler>> perEvent = INDEX.get(player.getUUID());
        if (perEvent == null) return;
        List<Handler> list = perEvent.get(event);
        if (list == null || list.isEmpty()) return;
        // Publish context to the ThreadLocal so context-aware action verbs
        // (damage_attacker, cancel_event, ...) can read it. Nesting-safe —
        // we restore the previous value after the loop.
        Object prev = ActionContextHolder.set(context);
        try {
            // Iterate a snapshot — a handler may remove itself (e.g. one-shot).
            for (Handler h : new ArrayList<>(list)) {
                h.accept(player, context);
            }
        } finally {
            ActionContextHolder.restore(prev);
        }
    }

    /** Peek handler count (diagnostics). */
    public static int handlerCount(ServerPlayer player, Event event) {
        Map<Event, List<Handler>> perEvent = INDEX.get(player.getUUID());
        if (perEvent == null) return 0;
        List<Handler> list = perEvent.get(event);
        return list == null ? 0 : list.size();
    }

    /** Chain modifier handlers for a player+event, returning the final value. */
    public static float dispatchModifier(ServerPlayer player, Event event, Object context, float base) {
        Map<Event, List<ModifierHandler>> perEvent = MOD_INDEX.get(player.getUUID());
        if (perEvent == null) return base;
        List<ModifierHandler> list = perEvent.get(event);
        if (list == null || list.isEmpty()) return base;
        Object prev = ActionContextHolder.set(context);
        try {
            float value = base;
            for (ModifierHandler m : new ArrayList<>(list)) {
                value = m.apply(player, context, value);
            }
            return value;
        } finally {
            ActionContextHolder.restore(prev);
        }
    }

    /** Clear all handlers for a player (call on logout). */
    public static void invalidate(UUID uuid) {
        INDEX.remove(uuid);
        MOD_INDEX.remove(uuid);
    }

    /** For regression tests: returns an immutable view of all events for a player. */
    public static Map<Event, List<Handler>> snapshot(ServerPlayer player) {
        Map<Event, List<Handler>> perEvent = INDEX.get(player.getUUID());
        return perEvent == null ? Collections.emptyMap() : Collections.unmodifiableMap(perEvent);
    }

    /** Convenience: dispatch with no context. */
    public static void dispatch(ServerPlayer player, Event event) {
        dispatch(player, event, null);
    }

    /** Hint to consumer: event context for {@link Event#HIT_TAKEN} is a {@code Float} damage amount. */
    public record HitTakenContext(float amount, net.minecraft.world.damagesource.DamageSource source) {}

    /** Hint to consumer: event context for {@link Event#KILL} is the killed entity. */
    public record KillContext(net.minecraft.world.entity.LivingEntity killed) {}

    /** Hint to consumer: event context for {@link Event#PROJECTILE_HIT}. */
    public record ProjectileHitContext(net.minecraft.world.entity.projectile.Projectile projectile,
                                        net.minecraft.world.phys.HitResult result) {}

    /** Context for crafting-table / inventory-crafting events. */
    public record CraftContext(net.minecraft.world.item.ItemStack result) {}

    /** Context for food-eaten events. */
    public record FoodContext(net.minecraft.world.item.ItemStack stack) {}

    /** Context for bonemeal / crop events. */
    public record BlockInteractContext(net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state) {}

    /** Context for breed / tame events. */
    public record EntityInteractContext(net.minecraft.world.entity.LivingEntity target) {}

    /** Context for trade events. */
    public record TradeContext(net.minecraft.world.item.trading.MerchantOffer offer) {}

    /** Context for advancement events. */
    public record AdvancementContext(net.minecraft.resources.ResourceLocation advancementId) {}
}
