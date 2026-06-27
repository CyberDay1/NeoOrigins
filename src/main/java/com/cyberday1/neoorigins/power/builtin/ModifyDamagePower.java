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
        Optional<String> targetGroup,
        Optional<EntityCondition> condition,
        // Apoli total-clamp ops, applied to the post-multiplier damage value:
        //   setTotal   = origins:set_total  → replace the value outright
        //   maxTotal   = origins:max_total  → CAP the value (Math.min(value, maxTotal))
        //   minTotal   = origins:min_total  → FLOOR the value (Math.max(value, minTotal))
        // (Despite the names, max_total is an upper cap and min_total a lower floor
        //  in Apoli.) Empty = that clamp is not applied. Order: setTotal, then
        //  maxTotal cap, then minTotal floor — matching Apoli's stage ordering.
        Optional<Float> setTotal,
        Optional<Float> maxTotal,
        Optional<Float> minTotal,
        String type
    ) implements PowerConfiguration {

        /**
         * Apply the configured arithmetic to an incoming damage value.
         * multiplier scales first, then the total-clamp ops in Apoli order.
         * Returns the final (non-NaN) damage; callers decide whether
         * {@code <= 0} cancels the event.
         */
        public float apply(float amount) {
            float v = amount * multiplier;
            if (setTotal.isPresent()) v = setTotal.get();
            if (maxTotal.isPresent()) v = Math.min(v, maxTotal.get());
            if (minTotal.isPresent()) v = Math.max(v, minTotal.get());
            if (!Float.isFinite(v)) v = Float.MAX_VALUE;
            return v;
        }

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
                Optional<String> grp = obj.has("target_group")
                    ? Optional.of(obj.get("target_group").getAsString()) : Optional.empty();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:modify_damage";

                Optional<EntityCondition> cond = obj.has("condition")
                    ? Optional.of(ConditionParser.parseField(obj, "condition", t))
                    : Optional.empty();

                Optional<Float> setTotal = obj.has("set_total")
                    ? Optional.of(obj.get("set_total").getAsFloat()) : Optional.empty();
                Optional<Float> maxTotal = obj.has("max_total")
                    ? Optional.of(obj.get("max_total").getAsFloat()) : Optional.empty();
                Optional<Float> minTotal = obj.has("min_total")
                    ? Optional.of(obj.get("min_total").getAsFloat()) : Optional.empty();

                return DataResult.success(Pair.of(
                    new Config(dir, mult, dmg, grp, cond, setTotal, maxTotal, minTotal, t), ops.empty()));
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
