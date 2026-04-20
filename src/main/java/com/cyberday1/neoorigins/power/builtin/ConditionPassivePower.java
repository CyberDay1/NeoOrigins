package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

/**
 * Generic condition-gated periodic action ("passive with a trigger").
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 4). Collapses the
 * behaviour of {@code biome_buff}, {@code damage_in_biome},
 * {@code damage_in_daylight}, {@code damage_in_water},
 * {@code burn_at_health_threshold}, {@code mobs_ignore_player},
 * {@code no_mob_spawns_nearby}, and {@code item_magnetism} into a single type.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:condition_passive",
 *   "interval": 20,
 *   "condition": { ... EntityCondition tree ... },
 *   "entity_action": { ... EntityAction tree ... },
 *   "else_action": { ... optional; runs when condition false ... }
 * }
 * }</pre>
 *
 * <p>Also supersedes {@code tick_action} when condition is absent.
 */
public class ConditionPassivePower extends PowerType<ConditionPassivePower.Config> {

    public record Config(
        int interval,
        EntityCondition condition,
        EntityAction action,
        EntityAction elseAction,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "condition_passive: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "condition_passive: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:condition_passive";
                int interval = obj.has("interval") ? Math.max(1, obj.get("interval").getAsInt()) : 20;

                EntityCondition cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();
                EntityAction action = obj.has("entity_action") && obj.get("entity_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("entity_action"), t)
                    : EntityAction.noop();
                EntityAction elseAction = obj.has("else_action") && obj.get("else_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("else_action"), t)
                    : EntityAction.noop();

                return DataResult.success(Pair.of(
                    new Config(interval, cond, action, elseAction, t),
                    ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % config.interval() != 0) return;
        if (config.condition().test(player)) {
            config.action().execute(player);
        } else {
            config.elseAction().execute(player);
        }
    }
}
