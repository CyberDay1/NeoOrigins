package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
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

import java.util.Optional;

public class ModifyDamagePower extends PowerType<ModifyDamagePower.Config> {

    public enum Direction { IN, OUT }

    public record Config(
        Direction direction,
        float multiplier,
        Optional<String> damageType,
        Optional<EntityCondition> condition,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "modify_damage: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "modify_damage: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();

                Direction dir = obj.has("direction") && "out".equalsIgnoreCase(obj.get("direction").getAsString())
                    ? Direction.OUT : Direction.IN;
                float mult = obj.has("multiplier") ? obj.get("multiplier").getAsFloat() : 1.0f;
                Optional<String> dmg = obj.has("damage_type")
                    ? Optional.of(obj.get("damage_type").getAsString()) : Optional.empty();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:modify_damage";

                Optional<EntityCondition> cond = obj.has("condition") && obj.get("condition").isJsonObject()
                    ? Optional.of(ConditionParser.parse(obj.getAsJsonObject("condition"), t))
                    : Optional.empty();

                return DataResult.success(Pair.of(
                    new Config(dir, mult, dmg, cond, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override public void onGranted(ServerPlayer player, Config config) {}
    @Override public void onRevoked(ServerPlayer player, Config config) {}
}
