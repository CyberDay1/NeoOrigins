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
 *   "modifier": { ... optional FloatModifier (or array of them) ... }
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

                EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();
                EntityAction action = obj.has("entity_action") && obj.get("entity_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("entity_action"), t)
                    : EntityAction.noop();
                FloatModifier modifier = obj.has("modifier")
                    ? ModifierParser.parseList(obj.get("modifier"), t)
                    : FloatModifier.identity();

                return DataResult.success(Pair.of(new Config(ev, cond, action, modifier, t), ops.empty()));
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

    private final java.util.Map<UUID, java.util.Map<Config, Tokens>> tokens = new ConcurrentHashMap<>();

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
                    if (!config.condition().test(sp)) return;
                    config.action().execute(sp);
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

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var perConfig = tokens.get(player.getUUID());
        if (perConfig == null) return;
        Tokens t = perConfig.remove(config);
        if (t != null) {
            EventPowerIndex.unregister(t.action());
            EventPowerIndex.unregister(t.modifier());
        }
        if (perConfig.isEmpty()) tokens.remove(player.getUUID());
    }
}
