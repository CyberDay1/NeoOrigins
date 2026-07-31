package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.modifier.FloatModifier;
import com.cyberday1.neoorigins.compat.modifier.ModifierParser;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic Origins-Classes event hook — fires an action and/or applies a float
 * modifier when a specific event occurs for the player.
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 6). Replaces the 20+
 * bespoke Origins-Classes hook power types ({@code better_enchanting},
 * {@code more_smoker_xp}, {@code crop_harvest_bonus}, {@code break_speed_modifier},
 * {@code natural_regen_modifier}, ...) with one configurable type.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:action_on_event",
 *   "event": "food_eaten",
 *   "condition": { ... optional EntityCondition ... },
 *   "entity_action": { ... optional side-effect action ... },
 *   "modifier": { ... optional FloatModifier (or array of them) ... },
 *   "power": "neoorigins:ground_slam"  // POWER_ACTIVATED only: id or array of ids
 * }
 * }</pre>
 *
 * <p>For action-style events (FOOD_EATEN, BLOCK_BREAK, etc.) set {@code entity_action}.
 * For modifier-style events (MOD_EXHAUSTION, MOD_NATURAL_REGEN, etc.) set {@code modifier}.
 * A single power may specify both — the action runs on dispatch sites that call
 * {@link EventPowerIndex#dispatch}, the modifier chains on sites that call
 * {@link EventPowerIndex#dispatchModifier}.
 */
public class ActionOnEventPower extends PowerType<ActionOnEventPower.Config> {

    public record Config(
        EventPowerIndex.Event event,
        EntityCondition condition,
        EntityAction action,
        FloatModifier modifier,
        // Optional block-position gate. Currently consulted for BLOCK_BREAK
        // and BLOCK_PLACE / BLOCK_USE events whose dispatch context carries
        // the BlockPos. Tester report 2026-04-27: pack authors author
        // `block_condition` on action_on_event with event=block_break and
        // expect filtering — without this field it was silently dropped at
        // codec time so the action ran on every block break.
        java.util.Optional<java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos>> blockCondition,
        // Optional mob-effect filter for the EFFECT_APPLIED event. `effect` is
        // a single registry id (e.g. "spore:mycelium_ef"); `effectTag` is a
        // TagKey<MobEffect> (e.g. "#minecraft:harmful" or a mod-defined tag).
        // Either or both may be set — match if the about-to-be-applied effect
        // equals `effect` OR is in `effectTag`. Ignored on all other events.
        java.util.Optional<net.minecraft.resources.Identifier> effect,
        java.util.Optional<net.minecraft.tags.TagKey<net.minecraft.world.effect.MobEffect>> effectTag,
        // Optional power-id filter for the POWER_ACTIVATED event. `power`
        // accepts a single id string or an array of ids; the action fires only
        // when the activated power's id is in the set. Empty = fire on ANY
        // power activation. Ignored on all other events.
        java.util.Optional<java.util.Set<net.minecraft.resources.Identifier>> powerFilter,
        // Optional hand filter for interaction events whose dispatch context
        // carries the triggering hand (BLOCK_USE / ENTITY_USE / VILLAGER_INTERACT
        // wrap the cancellable PlayerInteractEvent). Accepts `hands` (array)
        // or `hand` (single string) of "main_hand"/"off_hand" — the Apoli
        // action_on_block_use shape, needed so a main-hand-only legacy power
        // doesn't double-fire (vanilla dispatches RightClickBlock once per
        // hand). Fails closed when set but the context has no hand info,
        // mirroring the block_condition gate.
        java.util.Optional<java.util.Set<net.minecraft.world.InteractionHand>> hands,
        // Optional item filter for item-carrying events (ITEM_USE /
        // ITEM_USE_FINISH, whose dispatch context carries the used ItemStack in
        // a FoodContext or as a raw stack). Compiled from Apoli's
        // `item_condition` JSON via ItemConditionParser — the SAME predicate
        // path equipped_item / modify_inventory use. Tester report 2026-07-06:
        // an action_on_item_use with item_condition=ingredient(potion) fired on
        // EVERY item use because the translated item_condition was silently
        // dropped at codec time. Fails closed when set but the context carries
        // no item, mirroring the block_condition / hands gates. Ignored on
        // events that don't carry an item.
        java.util.Optional<com.cyberday1.neoorigins.compat.condition.ItemCondition> itemCondition,
        // Post-cleanse grace window in level ticks. When >0 AND the action
        // successfully cancels an EFFECT_APPLIED event (sets DO_NOT_APPLY),
        // grant `immunityTicks` ticks of full immunity to the same effect id
        // before the next dispatch can fire. Lets pack authors smooth out
        // probabilistic cancels (random_chance gates) so a successful "cleanse"
        // sticks for a beat instead of re-rolling on every bite.
        int immunityTicks,
        // Re-fire suppression window in ticks. When >0, after the entity_action
        // fires the action path goes inert for this power instance until the
        // cooldown elapses. Stored per-player in PlayerOriginData customFloats
        // keyed by the injected _power_id (the PreventDeathPower pattern), so
        // it survives respawn/relog and two action_on_event powers never share
        // a timer. The modifier path is NOT gated — cooldowns only make sense
        // for one-shot side effects, not value chains.
        int cooldownTicks,
        // Injected per power file by PowerDataManager (`_power_id`); uniquely
        // identifies THIS power instance for cooldown-state keying.
        String powerId,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "action_on_event: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "action_on_event: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:action_on_event";

                String evStr = obj.has("event") ? obj.get("event").getAsString() : "";
                EventPowerIndex.Event ev;
                try {
                    ev = EventPowerIndex.Event.valueOf(evStr.toUpperCase(Locale.ROOT));
                } catch (Exception e) {
                    return DataResult.error(() -> "action_on_event: unknown event '" + evStr + "'");
                }

                EntityCondition cond = ConditionParser.parseField(obj, "condition", t);
                EntityAction action = ActionParser.parseField(obj, "entity_action", t);
                FloatModifier modifier = obj.has("modifier")
                    ? ModifierParser.parseList(obj.get("modifier"), t)
                    : FloatModifier.identity();
                // Compile the optional block_condition into a BiPredicate so
                // dispatch only pays a registry lookup, not JSON parsing.
                java.util.Optional<java.util.function.BiPredicate<ServerPlayer, net.minecraft.core.BlockPos>> blockCond =
                    (obj.has("block_condition") && obj.get("block_condition").isJsonObject())
                        ? java.util.Optional.of(com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader
                            .compileBlockPredicate(obj.getAsJsonObject("block_condition"),
                                obj.has("_power_id") ? obj.get("_power_id").getAsString() : t))
                        : java.util.Optional.empty();

                // Optional EFFECT_APPLIED filter: `effect` is a single id,
                // `effect_tag` is a TagKey<MobEffect> (the leading '#' is
                // optional in JSON for symmetry with vanilla item-tag syntax).
                java.util.Optional<net.minecraft.resources.Identifier> effectFilter =
                    (obj.has("effect") && obj.get("effect").isJsonPrimitive())
                        ? java.util.Optional.ofNullable(net.minecraft.resources.Identifier
                            .tryParse(obj.get("effect").getAsString()))
                        : java.util.Optional.empty();
                java.util.Optional<net.minecraft.tags.TagKey<net.minecraft.world.effect.MobEffect>> effectTagFilter;
                if (obj.has("effect_tag") && obj.get("effect_tag").isJsonPrimitive()) {
                    String raw = obj.get("effect_tag").getAsString();
                    if (raw.startsWith("#")) raw = raw.substring(1);
                    net.minecraft.resources.Identifier tagId =
                        net.minecraft.resources.Identifier.tryParse(raw);
                    effectTagFilter = (tagId == null)
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.MOB_EFFECT, tagId));
                } else {
                    effectTagFilter = java.util.Optional.empty();
                }

                int immunity = (obj.has("immunity_ticks") && obj.get("immunity_ticks").isJsonPrimitive())
                    ? Math.max(0, obj.get("immunity_ticks").getAsInt())
                    : 0;

                int cooldown = (obj.has("cooldown_ticks") && obj.get("cooldown_ticks").isJsonPrimitive())
                    ? Math.max(0, obj.get("cooldown_ticks").getAsInt())
                    : 0;
                String pid = obj.has("_power_id")
                    ? obj.get("_power_id").getAsString() : "neoorigins:action_on_event";

                // Optional POWER_ACTIVATED filter: `power` is a single power id
                // or an array of ids. Unparseable ids are skipped with a warn so
                // a typo narrows the filter instead of silently matching all.
                java.util.Optional<java.util.Set<net.minecraft.resources.Identifier>> powerFilter =
                    java.util.Optional.empty();
                if (obj.has("power")) {
                    java.util.Set<net.minecraft.resources.Identifier> ids = new java.util.HashSet<>();
                    JsonElement pf = obj.get("power");
                    java.util.List<JsonElement> raw = pf.isJsonArray()
                        ? pf.getAsJsonArray().asList()
                        : java.util.List.of(pf);
                    for (JsonElement el : raw) {
                        if (!el.isJsonPrimitive()) continue;
                        net.minecraft.resources.Identifier rl =
                            net.minecraft.resources.Identifier.tryParse(el.getAsString());
                        if (rl != null) ids.add(rl);
                        else NeoOrigins.LOGGER.warn(
                            "action_on_event ({}): unparseable power id '{}' in `power` filter",
                            t, el.getAsString());
                    }
                    if (!ids.isEmpty()) powerFilter = java.util.Optional.of(java.util.Set.copyOf(ids));
                }

                // Optional hand filter: `hands` (array) or `hand` (single
                // string). Values are the Apoli names "main_hand"/"off_hand";
                // unknown strings are skipped with a warn.
                java.util.Optional<java.util.Set<net.minecraft.world.InteractionHand>> handFilter =
                    java.util.Optional.empty();
                JsonElement handsEl = obj.has("hands") ? obj.get("hands")
                    : (obj.has("hand") ? obj.get("hand") : null);
                if (handsEl != null) {
                    java.util.Set<net.minecraft.world.InteractionHand> parsed =
                        java.util.EnumSet.noneOf(net.minecraft.world.InteractionHand.class);
                    java.util.List<JsonElement> raw = handsEl.isJsonArray()
                        ? handsEl.getAsJsonArray().asList()
                        : java.util.List.of(handsEl);
                    for (JsonElement el : raw) {
                        if (!el.isJsonPrimitive()) continue;
                        switch (el.getAsString().toLowerCase(Locale.ROOT)) {
                            case "main_hand", "mainhand" -> parsed.add(net.minecraft.world.InteractionHand.MAIN_HAND);
                            case "off_hand", "offhand"   -> parsed.add(net.minecraft.world.InteractionHand.OFF_HAND);
                            default -> NeoOrigins.LOGGER.warn(
                                "action_on_event ({}): unknown hand '{}' in `hands` filter",
                                t, el.getAsString());
                        }
                    }
                    if (!parsed.isEmpty()) handFilter = java.util.Optional.of(parsed);
                }

                // Optional item_condition: compile the Apoli item-condition JSON
                // into an ItemCondition predicate using the same parser
                // equipped_item / modify_inventory use. Consulted only for
                // item-carrying events (ITEM_USE / ITEM_USE_FINISH); on other
                // events it's simply never read.
                java.util.Optional<com.cyberday1.neoorigins.compat.condition.ItemCondition> itemCond =
                    (obj.has("item_condition") && obj.get("item_condition").isJsonObject())
                        ? java.util.Optional.of(com.cyberday1.neoorigins.compat.condition.ItemConditionParser
                            .parse(obj.getAsJsonObject("item_condition")))
                        : java.util.Optional.empty();

                return DataResult.success(Pair.of(
                    new Config(ev, cond, action, modifier, blockCond,
                        effectFilter, effectTagFilter, powerFilter, handFilter, itemCond, immunity, cooldown, pid, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    /** Tracks both action and modifier tokens per-player per-config so revoke is clean. */
    private record Tokens(EventPowerIndex.Token action, EventPowerIndex.Token modifier) {}

    private static final java.util.Map<UUID, java.util.Map<Config, Tokens>> tokens = new ConcurrentHashMap<>();

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        // Idempotent-grant: PowerType.onLogin() + onRespawn() default to calling
        // onGranted, so without this cleanup each login/respawn/origin-swap
        // would stack another handler on top of the prior one. For modifier
        // events that compound on chain dispatch (MOD_EXHAUSTION multiplication
        // etc.), leaked handlers make the power N× stronger where N is the
        // number of life cycles — the "Internal Furnace drains 10× too fast"
        // report was 1.5^5 ≈ 7.6× after five respawns.
        var perConfig = tokens.get(player.getUUID());
        if (perConfig != null) {
            Tokens existing = perConfig.remove(config);
            if (existing != null) {
                EventPowerIndex.unregister(existing.action());
                EventPowerIndex.unregister(existing.modifier());
            }
        }

        EventPowerIndex.Token actionTok = null;
        EventPowerIndex.Token modTok = null;

        // Register the side-effect path if an action was declared.
        if (config.action() != EntityAction.noop()) {
            EventPowerIndex.Handler handler = (sp, ctx) -> {
                try {
                    // EFFECT_APPLIED filter gate: only runs for that event; for
                    // other events the optionals are simply unread. Match if
                    // either `effect` or `effect_tag` matches (OR semantics).
                    // If neither is set, the action fires for any effect — the
                    // documented "catch-all" shape.
                    if (config.event() == EventPowerIndex.Event.EFFECT_APPLIED
                            && (config.effect().isPresent() || config.effectTag().isPresent())) {
                        if (!(ctx instanceof EventPowerIndex.EffectAppliedContext ec)) return;
                        boolean match = false;
                        if (config.effect().isPresent() && config.effect().get().equals(ec.effectId())) {
                            match = true;
                        }
                        if (!match && config.effectTag().isPresent()) {
                            match = ec.effectInstance().getEffect().is(config.effectTag().get());
                        }
                        if (!match) return;
                    }
                    // POWER_ACTIVATED filter gate: only consulted for that
                    // event. No filter = fire on any activation.
                    if (config.event() == EventPowerIndex.Event.POWER_ACTIVATED
                            && config.powerFilter().isPresent()) {
                        if (!(ctx instanceof EventPowerIndex.PowerActivatedContext pc)) return;
                        if (!config.powerFilter().get().contains(pc.powerId())) return;
                    }
                    if (!config.condition().test(sp)) return;
                    // hands gate: only fire when the dispatch context carries
                    // the triggering hand and it is in the configured set.
                    // Fails closed on hand-less contexts, same as block_condition.
                    if (config.hands().isPresent()) {
                        net.minecraft.world.InteractionHand hand = extractHand(ctx);
                        if (hand == null || !config.hands().get().contains(hand)) return;
                    }
                    // item_condition gate: extract the used ItemStack from an
                    // item-carrying context (FoodContext for ITEM_USE /
                    // ITEM_USE_FINISH, or a raw ItemStack) and run the compiled
                    // ItemCondition predicate. Fails closed when set but the
                    // context has no item — an item filter with no item to test
                    // is no match — mirroring the block_condition / hands gates.
                    // This is the fix for action_on_item_use firing on EVERY
                    // item use instead of only the filtered item.
                    if (config.itemCondition().isPresent()) {
                        net.minecraft.world.item.ItemStack used = extractItemStack(ctx);
                        if (used == null) return;
                        if (!config.itemCondition().get().test(used)) return;
                    }
                    // block_condition gate: extract the BlockPos from a known
                    // block-event context shape and run the predicate. Skips
                    // silently if the context doesn't carry block info — same
                    // behaviour as if the gate evaluated false.
                    if (config.blockCondition().isPresent()) {
                        net.minecraft.core.BlockPos pos = extractBlockPos(ctx);
                        if (pos == null) return;
                        if (!config.blockCondition().get().test(sp, pos)) return;
                    }
                    // cooldown_ticks gate: after the action fires, suppress
                    // re-fires for this power instance until the persisted
                    // per-player timer (decremented in onTick) runs out.
                    // Checked LAST so a suppressed dispatch is exactly "all
                    // gates passed but the timer is still running".
                    if (config.cooldownTicks() > 0) {
                        var data = sp.getData(
                            com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
                        if (data.getCustomFloat(cooldownKey(config), 0) > 0) return;
                    }
                    config.action().execute(sp);
                    if (config.cooldownTicks() > 0) {
                        sp.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData())
                            .setCustomFloat(cooldownKey(config), config.cooldownTicks());
                    }
                    // Post-action grace: if this is an EFFECT_APPLIED dispatch
                    // AND the action actually cancelled it (setResult = DO_NOT_APPLY)
                    // AND the author asked for immunity_ticks, open the window so
                    // subsequent dispatches for the same effect short-circuit
                    // without re-rolling.
                    if (config.immunityTicks() > 0
                            && config.event() == EventPowerIndex.Event.EFFECT_APPLIED
                            && ctx instanceof EventPowerIndex.EffectAppliedContext ec
                            && ec.event() != null
                            && ec.event().getResult() == net.neoforged.neoforge.event.entity.living
                                .MobEffectEvent.Applicable.Result.DO_NOT_APPLY) {
                        EventPowerIndex.setEffectGrace(sp.getUUID(), ec.effectId(),
                            sp.level().getGameTime() + config.immunityTicks());
                    }
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("action_on_event handler error ({}): {}",
                        config.event(), e.getMessage());
                }
            };
            actionTok = EventPowerIndex.register(player, config.event(), handler);
        }

        // Register the modifier path if a non-identity modifier was declared.
        if (config.modifier() != FloatModifier.identity()) {
            EventPowerIndex.ModifierHandler modHandler = (sp, ctx, base) -> {
                try {
                    if (!config.condition().test(sp)) return base;
                    return config.modifier().apply(base);
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("action_on_event modifier error ({}): {}",
                        config.event(), e.getMessage());
                    return base;
                }
            };
            modTok = EventPowerIndex.registerModifier(player, config.event(), modHandler);
        }

        tokens.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
            .put(config, new Tokens(actionTok, modTok));
    }

    /**
     * Best-effort BlockPos extraction from a block-event dispatch context.
     * Returns null if the context isn't block-shaped, in which case the
     * block_condition gate fails closed (action skipped).
     */
    private static net.minecraft.core.BlockPos extractBlockPos(Object ctx) {
        if (ctx instanceof net.neoforged.neoforge.event.level.BlockEvent be) {
            return be.getPos();
        }
        if (ctx instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock rcb) {
            return rcb.getPos();
        }
        if (ctx instanceof EventPowerIndex.BlockInteractContext bic) {
            return bic.pos();
        }
        return null;
    }

    /**
     * Best-effort InteractionHand extraction from an interaction dispatch
     * context. BLOCK_USE / ENTITY_USE contexts wrap the cancellable
     * PlayerInteractEvent, which carries the hand; anything else yields null
     * and the hands gate fails closed.
     */
    private static net.minecraft.world.InteractionHand extractHand(Object ctx) {
        if (ctx instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent pie) {
            return pie.getHand();
        }
        if (ctx instanceof EventPowerIndex.BlockInteractContext bic
                && bic.event() instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent pie) {
            return pie.getHand();
        }
        if (ctx instanceof EventPowerIndex.EntityInteractContext eic
                && eic.event() instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent pie) {
            return pie.getHand();
        }
        return null;
    }

    /**
     * Best-effort ItemStack extraction from an item-carrying dispatch context.
     * ITEM_USE dispatches a {@link EventPowerIndex.FoodContext} (stack + cancel
     * event); ITEM_USE_FINISH dispatches either a FoodContext or a raw
     * ItemStack. Returns null (empty stacks included) if the context carries no
     * usable item, in which case the item_condition gate fails closed.
     */
    private static net.minecraft.world.item.ItemStack extractItemStack(Object ctx) {
        if (ctx instanceof EventPowerIndex.FoodContext fc) {
            return (fc.stack() == null || fc.stack().isEmpty()) ? null : fc.stack();
        }
        if (ctx instanceof net.minecraft.world.item.ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        return null;
    }

    /** Cooldown-state key in PlayerOriginData customFloats — per power instance
     *  (keyed by the injected _power_id), mirroring PreventDeathPower. */
    private static String cooldownKey(Config config) {
        return "action_on_event_cd:" + config.powerId();
    }

    /** Decrement the persisted cooldown timer, PreventDeathPower-style. */
    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.cooldownTicks() <= 0) return;
        var data = player.getData(
            com.cyberday1.neoorigins.attachment.OriginAttachments.originData());
        float remaining = data.getCustomFloat(cooldownKey(config), 0);
        if (remaining > 0) {
            data.setCustomFloat(cooldownKey(config), remaining - 1);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var perConfig = tokens.get(player.getUUID());
        if (perConfig != null) {
            Tokens t = perConfig.remove(config);
            if (t != null) {
                EventPowerIndex.unregister(t.action());
                EventPowerIndex.unregister(t.modifier());
            }
            if (perConfig.isEmpty()) tokens.remove(player.getUUID());
        }
        // Clear the persisted cooldown so a stale timer doesn't carry over to
        // the next holder of this power id (origin change / orb reroll) —
        // same reasoning as PreventDeathPower.onRevoked.
        if (config.cooldownTicks() > 0) {
            player.getData(com.cyberday1.neoorigins.attachment.OriginAttachments.originData())
                .setCustomFloat(cooldownKey(config), 0);
        }
    }

    /**
     * Remove all cached tokens for a player. Called from
     * {@code ActiveOriginService.revokeAllPowers} as a safety sweep
     * after individual {@code onRevoked} calls, mirroring the
     * attribute-modifier purge pattern.
     */
    public static void clearTokens(UUID uuid) {
        tokens.remove(uuid);
    }
}
