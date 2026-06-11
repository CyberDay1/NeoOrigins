package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
        FOOD_EATEN,             // (misnomer — fires at use START, cancellable; used for food_restriction)
        FOOD_FINISHED,          // player actually finished eating a food item (post-eat, non-cancellable)
        ADVANCEMENT_EARNED,
        TRADE_COMPLETED,        // villager trade finished
        VILLAGER_INTERACT,      // right-click villager

        // ----- Origin / power lifecycle -----
        GAINED,                 // power was just granted to the player
        LOST,                   // power was just revoked from the player
        CHOSEN,                 // player chose an origin (ChooseOriginPayload)
        POWER_ACTIVATED,        // another power was SUCCESSFULLY activated (active fired past
                                // cooldown/cost gates, or a toggle flipped either direction).
                                // Context: PowerActivatedContext. Nested activations triggered
                                // from inside a POWER_ACTIVATED handler do NOT re-dispatch
                                // (recursion guard in dispatchPowerActivated).

        // ----- Extended interaction / movement -----
        WAKE_UP,                // player woke from sleeping
        LAND,                   // player hit the ground after falling
        BLOCK_USE,              // right-click on a block (general)
        ENTITY_USE,             // right-click on an entity (general, not villager-specific)
        ITEM_PICKUP,            // item entity picked up
        ITEM_USE_FINISH,        // finished using an item (distinct from ITEM_USE which fires at use-start)

        // ----- Status-effect application -----
        EFFECT_APPLIED,         // a mob effect is about to be applied (fires on MobEffectEvent.Applicable;
                                // dispatched AFTER EffectImmunityPower/EntityGroupPower checks, so handlers
                                // see only effects those rules let through. Cancel via neoorigins:cancel_event.)

        // ----- Origins-Classes hooks: modifiers (return a float) -----
        MOD_EXHAUSTION,         // hunger drain multiplier
        MOD_NATURAL_REGEN,      // natural heal amount multiplier
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

    /** Per-player per-effect-id grace expiry (level game-time, monotonic).
     *  Populated by {@code action_on_event} on a successful cleanse of an
     *  {@link Event#EFFECT_APPLIED}; consulted by the dispatch site
     *  ({@code CombatPowerEvents.onMobEffectApplicable}) which short-circuits
     *  to {@code DO_NOT_APPLY} if the window is still open. Cleared on logout
     *  via {@link #invalidate(UUID)}. */
    private static final Map<UUID, Map<net.minecraft.resources.ResourceLocation, Long>> EFFECT_GRACE =
        new ConcurrentHashMap<>();

    /** Extend (max-merge) the grace window for {@code effectId} on {@code uuid} to {@code expiryGameTime}. */
    public static void setEffectGrace(UUID uuid, net.minecraft.resources.ResourceLocation effectId,
                                      long expiryGameTime) {
        EFFECT_GRACE.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
            .merge(effectId, expiryGameTime, Math::max);
    }

    /** True if {@code uuid} currently has an active grace window for {@code effectId} at {@code nowGameTime}. */
    public static boolean hasEffectGrace(UUID uuid, net.minecraft.resources.ResourceLocation effectId,
                                         long nowGameTime) {
        var perEffect = EFFECT_GRACE.get(uuid);
        if (perEffect == null) return false;
        Long expiry = perEffect.get(effectId);
        if (expiry == null) return false;
        if (nowGameTime < expiry) return true;
        // Expired — sweep so the map doesn't grow unbounded across many short-lived effects.
        perEffect.remove(effectId);
        if (perEffect.isEmpty()) EFFECT_GRACE.remove(uuid);
        return false;
    }

    /** Register a handler for a player+event. Typically called in {@code PowerType.onGranted}. */
    public static Token register(ServerPlayer player, Event event, Handler handler) {
        UUID uuid = player.getUUID();
        INDEX.computeIfAbsent(uuid, k -> new EnumMap<>(Event.class))
             .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
             .add(handler);
        return new Token(uuid, event, handler, null);
    }

    /** Register a modifier handler (for MOD_* events that chain a float value). */
    public static Token registerModifier(ServerPlayer player, Event event, ModifierHandler modifier) {
        UUID uuid = player.getUUID();
        MOD_INDEX.computeIfAbsent(uuid, k -> new EnumMap<>(Event.class))
             .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
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

    /**
     * Remove ALL handlers (action + modifier) for the given player UUID.
     * Called from {@code revokeAllPowers} as a belt-and-suspenders cleanup —
     * individual {@code onRevoked} calls should have already unregistered
     * their own handlers, but if any were missed (e.g. due to Config record
     * equality mismatch from lambda identity) this sweep prevents stale
     * handlers from persisting after an origin reset.
     */
    public static void clearAll(UUID uuid) {
        INDEX.remove(uuid);
        MOD_INDEX.remove(uuid);
        EFFECT_GRACE.remove(uuid);
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
            // CopyOnWriteArrayList iterator is snapshot-safe; a handler may
            // remove itself (e.g. one-shot) without ConcurrentModification.
            for (Handler h : list) {
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
            for (ModifierHandler m : list) {
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
        EFFECT_GRACE.remove(uuid);
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

    /**
     * Context for {@link Event#HIT_TAKEN}. Carries the damage amount/source plus
     * (when available) the cancellable {@code LivingIncomingDamageEvent} so
     * {@code neoorigins:cancel_event} can negate the hit. Mirrors the
     * {@link FoodContext} pattern.
     */
    public record HitTakenContext(float amount, net.minecraft.world.damagesource.DamageSource source,
                                   net.neoforged.bus.api.ICancellableEvent event) {
        /** Amount+source ctor for call sites that don't need cancel semantics. */
        public HitTakenContext(float amount, net.minecraft.world.damagesource.DamageSource source) {
            this(amount, source, null);
        }
    }

    /**
     * Context for {@link Event#KILL}: the killed entity plus (when available)
     * the cancellable {@code LivingDeathEvent} so {@code neoorigins:cancel_event}
     * can spare the victim. The dispatch site patches the victim's health back
     * to a positive value after a cancel so it doesn't sit dead-at-0-HP.
     */
    public record KillContext(net.minecraft.world.entity.LivingEntity killed,
                              net.neoforged.bus.api.ICancellableEvent event) {
        /** Killed-only ctor for call sites that don't need cancel semantics. */
        public KillContext(net.minecraft.world.entity.LivingEntity killed) {
            this(killed, null);
        }
    }

    /**
     * Context for {@link Event#PROJECTILE_HIT}. Carries the projectile and hit
     * result plus (when available) the cancellable {@code ProjectileImpactEvent}
     * so {@code neoorigins:cancel_event} can negate the impact.
     */
    public record ProjectileHitContext(net.minecraft.world.entity.projectile.Projectile projectile,
                                        net.minecraft.world.phys.HitResult result,
                                        net.neoforged.bus.api.ICancellableEvent event) {
        /** Projectile+result ctor for call sites that don't need cancel semantics. */
        public ProjectileHitContext(net.minecraft.world.entity.projectile.Projectile projectile,
                                    net.minecraft.world.phys.HitResult result) {
            this(projectile, result, null);
        }
    }

    /** Context for crafting-table / inventory-crafting events. */
    public record CraftContext(net.minecraft.world.item.ItemStack result) {}

    /**
     * Context for food-eaten events. Carries both the held {@link
     * net.minecraft.world.item.ItemStack} (for item-tag conditions) and the
     * cancellable wrapper event (so {@code neoorigins:cancel_event} can veto
     * the eat).
     */
    public record FoodContext(net.minecraft.world.item.ItemStack stack,
                               net.neoforged.bus.api.ICancellableEvent event) {
        /** Stack-only ctor for call sites that don't need cancel semantics. */
        public FoodContext(net.minecraft.world.item.ItemStack stack) { this(stack, null); }
    }

    /**
     * Context for bonemeal / crop / block-use events. Carries the position and
     * state plus (when the dispatch site's underlying NeoForge event is
     * cancellable) the wrapper event so {@code neoorigins:cancel_event} can
     * veto the interaction. Mirrors the {@link FoodContext} pattern.
     */
    public record BlockInteractContext(net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state,
                                        net.neoforged.bus.api.ICancellableEvent event) {
        /** Pos+state-only ctor for call sites that don't need cancel semantics. */
        public BlockInteractContext(net.minecraft.core.BlockPos pos,
                                    net.minecraft.world.level.block.state.BlockState state) {
            this(pos, state, null);
        }
    }

    /** Context for {@link Event#POWER_ACTIVATED}: the id of the power that just fired. */
    public record PowerActivatedContext(net.minecraft.resources.ResourceLocation powerId) {}

    /**
     * Re-entrancy guard for {@link #dispatchPowerActivated}. True while a
     * POWER_ACTIVATED dispatch is running on this thread, so a listener whose
     * entity_action activates another power (e.g. via {@code execute_command}
     * running an activation command) can't loop A→B→A forever. The nested
     * activation itself still happens — it just doesn't re-notify listeners.
     */
    private static final ThreadLocal<Boolean> IN_POWER_ACTIVATED_DISPATCH =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Notify listeners that {@code powerId} was successfully activated.
     * Single chokepoint for all activation success paths (AbstractActivePower,
     * AbstractTogglePower, CompatPower). No-ops when called re-entrantly from
     * inside another POWER_ACTIVATED dispatch.
     */
    public static void dispatchPowerActivated(ServerPlayer player,
                                              net.minecraft.resources.ResourceLocation powerId) {
        if (powerId == null) return;
        if (IN_POWER_ACTIVATED_DISPATCH.get()) return;
        IN_POWER_ACTIVATED_DISPATCH.set(Boolean.TRUE);
        try {
            dispatch(player, Event.POWER_ACTIVATED, new PowerActivatedContext(powerId));
        } finally {
            IN_POWER_ACTIVATED_DISPATCH.set(Boolean.FALSE);
        }
    }

    /**
     * Context for breed / tame / entity-use / villager-interact events. Carries
     * the target plus (when the dispatch site's underlying NeoForge event is
     * cancellable) the wrapper event so {@code neoorigins:cancel_event} can
     * veto the interaction. Mirrors the {@link FoodContext} pattern.
     */
    public record EntityInteractContext(net.minecraft.world.entity.LivingEntity target,
                                        net.neoforged.bus.api.ICancellableEvent event) {
        /** Target-only ctor for call sites that don't need cancel semantics. */
        public EntityInteractContext(net.minecraft.world.entity.LivingEntity target) {
            this(target, null);
        }
    }

    /**
     * Dispatch context naming a non-actor <em>Entity</em> that should act as the
     * origin of a sub-action — published by {@code selector_action} for each
     * entity its vanilla selector resolved. Unlike {@link EntityInteractContext}
     * (which is {@link net.minecraft.world.entity.LivingEntity}-typed and feeds the
     * bientity {@code target_action} chain), this carries a bare {@link net.minecraft.world.entity.Entity}
     * so non-living origins such as {@code area_effect_cloud} can be the source of a
     * {@code fire_projectile} (the Toxophilite hyper_multishot AEC-rotation trick).
     * The actor (power holder) still arrives as the {@code EntityAction} arg; this
     * only redirects the spatial origin/rotation.
     */
    public record SourceEntityContext(net.minecraft.world.entity.Entity sourceEntity) {}

    /**
     * Dispatch context for a <em>block</em> impact — the block on the other side
     * of a projectile/raycast interaction. Sibling of {@link EntityInteractContext}
     * (the entity equivalent) on the same {@link ActionContextHolder} seam. The
     * block-target verbs ({@code strip}/{@code till}/{@code path}/{@code grow}/
     * {@code transform_block}) and the {@code block_target_action} wrapper resolve
     * it via {@link com.cyberday1.neoorigins.compat.action.ActionParser#extractBlockTarget}.
     * Carries the {@link net.minecraft.server.level.ServerLevel} and the impacted
     * {@link net.minecraft.core.BlockPos}; the actor arrives separately as the
     * {@code EntityAction}/{@code BlockTargetAction} arg.
     */
    public record BlockHitContext(net.minecraft.server.level.ServerLevel level,
                                  net.minecraft.core.BlockPos pos) {}

    /** Context for trade events. */
    public record TradeContext(net.minecraft.world.item.trading.MerchantOffer offer) {}

    /** Context for advancement events. */
    public record AdvancementContext(net.minecraft.resources.ResourceLocation advancementId) {}

    /**
     * Context for {@link Event#EFFECT_APPLIED}. Carries the about-to-be-applied
     * effect instance, its registry id (pre-resolved so handlers don't repeat
     * the lookup), and the cancellable NeoForge event so
     * {@code neoorigins:cancel_event} can deny the application via
     * {@code setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY)}.
     */
    public record EffectAppliedContext(
        net.minecraft.world.effect.MobEffectInstance effectInstance,
        net.minecraft.resources.ResourceLocation effectId,
        net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {}
}
