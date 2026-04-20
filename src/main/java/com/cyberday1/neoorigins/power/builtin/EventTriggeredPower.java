package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
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
 * Generic event-driven power: fires an action (optionally gated by a condition)
 * when a specific event occurs for the player.
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 5). Replaces
 * {@code action_on_kill}, {@code action_on_hit_taken}, {@code scare_entities},
 * {@code thorns_aura}, and analogous event-firing powers. Dispatch is routed
 * through {@link EventPowerIndex} for O(1) event fan-out.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:event_triggered",
 *   "event": "kill" | "hit_taken" | "attack" | "respawn" | "tick" | ...,
 *   "condition": { ... },
 *   "entity_action": { ... }
 * }
 * }</pre>
 */
public class EventTriggeredPower extends PowerType<EventTriggeredPower.Config> {

    public record Config(
        EventPowerIndex.Event event,
        EntityCondition condition,
        EntityAction action,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "event_triggered: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "event_triggered: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:event_triggered";

                String evStr = obj.has("event") ? obj.get("event").getAsString() : "";
                EventPowerIndex.Event ev;
                try {
                    ev = EventPowerIndex.Event.valueOf(evStr.toUpperCase(Locale.ROOT));
                } catch (Exception e) {
                    return DataResult.error(() -> "event_triggered: unknown event '" + evStr + "'");
                }

                EntityAction action = obj.has("entity_action") && obj.get("entity_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("entity_action"), t)
                    : EntityAction.noop();
                EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();

                return DataResult.success(Pair.of(new Config(ev, cond, action, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    /** Tracks registered tokens per-player per-config so we can unregister on revoke. */
    private final java.util.Map<UUID, java.util.Map<Config, EventPowerIndex.Token>> tokens = new ConcurrentHashMap<>();

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        EventPowerIndex.Handler handler = (sp, ctx) -> {
            try {
                if (!config.condition().test(sp)) return;
                config.action().execute(sp);
            } catch (Exception e) {
                NeoOrigins.LOGGER.warn("event_triggered handler error ({}): {}",
                    config.event(), e.getMessage());
            }
        };
        EventPowerIndex.Token token = EventPowerIndex.register(player, config.event(), handler);
        tokens.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(config, token);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        var perConfig = tokens.get(player.getUUID());
        if (perConfig == null) return;
        EventPowerIndex.Token token = perConfig.remove(config);
        if (token != null) EventPowerIndex.unregister(token);
        if (perConfig.isEmpty()) tokens.remove(player.getUUID());
    }
}
