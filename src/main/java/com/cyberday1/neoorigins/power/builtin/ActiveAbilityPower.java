package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

/**
 * Generic cooldown-gated active (keybind) ability.
 *
 * <p>Part of the 2.0 power-type consolidation (Phase 1). Collapses the
 * behaviour of ~15 bespoke active power types into a single type whose effect
 * is described by an {@code entity_action} tree, optionally gated by an
 * {@code condition}. Actions are compiled once at power-load time via
 * {@link ActionParser}; conditions via {@link ConditionParser}.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:active_ability",
 *   "cooldown_ticks": 60,
 *   "condition": { ... optional EntityCondition tree ... },
 *   "entity_action": { "type": "origins:and", "actions": [ ... ] }
 * }
 * }</pre>
 *
 * <p>Legacy active types (active_teleport, active_dash, etc.) remain registered
 * during the deprecation window. Their JSON can be migrated to this type via
 * the {@code migrateLegacyPowers} gradle task; external packs are also covered
 * by {@link com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases} once
 * Phase 1 field remappers are registered.
 */
public class ActiveAbilityPower extends AbstractActivePower<ActiveAbilityPower.Config> {

    public record Config(
        int cooldownTicks,
        int hungerCost,
        EntityAction action,
        EntityCondition condition,
        String type
    ) implements AbstractActivePower.Config {

        @Override public int cooldownTicks() { return cooldownTicks; }
        @Override public int hungerCost() { return hungerCost; }

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "active_ability: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "active_ability: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                int cooldown = obj.has("cooldown_ticks") ? obj.get("cooldown_ticks").getAsInt() : 60;
                int hunger = obj.has("hunger_cost") ? obj.get("hunger_cost").getAsInt() : 0;
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:active_ability";
                EntityAction action = obj.has("entity_action") && obj.get("entity_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("entity_action"), t)
                    : EntityAction.noop();
                EntityCondition condition = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("condition"), t)
                    : EntityCondition.alwaysTrue();
                return DataResult.success(Pair.of(new Config(cooldown, hunger, action, condition, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                // Compiled lambdas don't round-trip — sync payloads carry only type ID + display.
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    protected boolean execute(ServerPlayer player, Config config) {
        try {
            if (!config.condition().test(player)) return false;
            config.action().execute(player);
            return true;
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("active_ability fired with error: {}", e.getMessage());
            return false;
        }
    }
}
