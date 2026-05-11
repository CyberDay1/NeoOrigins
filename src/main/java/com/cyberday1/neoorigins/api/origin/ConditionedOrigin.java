package com.cyberday1.neoorigins.api.origin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * An origin reference with an optional layer condition.
 *
 * <p>Accepts three JSON shapes:
 * <ul>
 *   <li>Plain string: {@code "neoorigins:human"}</li>
 *   <li>Object with singular origin: {@code {"origin": "neoorigins:human", "condition": {...}}}</li>
 *   <li>Object without condition: {@code {"origin": "neoorigins:human"}}</li>
 * </ul>
 *
 * <p>The condition, when present, is parsed via {@link OriginCondition#parse(JsonObject)}
 * and evaluated against the player's layer→origin choices map. Origins whose
 * conditions fail are hidden from the picker and excluded from server validation.
 */
public record ConditionedOrigin(
    Identifier origin,
    Optional<OriginCondition> condition
) {
    /**
     * Codec that handles both plain string and object formats.
     * Uses a custom codec to support OriginCondition parsing/serialization.
     */
    public static final Codec<ConditionedOrigin> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ConditionedOrigin, T>> decode(DynamicOps<T> ops, T input) {
            // Try plain string first
            DataResult<String> stringResult = ops.getStringValue(input);
            if (stringResult.isSuccess()) {
                String s = stringResult.getOrThrow();
                Identifier rl = Identifier.parse(s);
                return DataResult.success(Pair.of(new ConditionedOrigin(rl, Optional.empty()), ops.empty()));
            }

            // Try object format
            DataResult<MapLike<T>> mapResult = ops.getMap(input);
            if (mapResult.isSuccess()) {
                MapLike<T> map = mapResult.getOrThrow();
                T originVal = map.get("origin");
                if (originVal == null) {
                    return DataResult.error(() -> "ConditionedOrigin object missing 'origin' field");
                }
                DataResult<String> originStr = ops.getStringValue(originVal);
                if (!originStr.isSuccess()) {
                    return DataResult.error(() -> "ConditionedOrigin 'origin' is not a string");
                }
                Identifier rl = Identifier.parse(originStr.getOrThrow());

                // Parse condition if present
                Optional<OriginCondition> cond = Optional.empty();
                T condVal = map.get("condition");
                if (condVal != null) {
                    // Convert to JsonObject for OriginCondition.parse
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, condVal);
                    if (json.isJsonObject()) {
                        OriginCondition parsed = OriginCondition.parse(json.getAsJsonObject());
                        if (parsed != null) cond = Optional.of(parsed);
                    }
                }

                return DataResult.success(Pair.of(new ConditionedOrigin(rl, cond), ops.empty()));
            }

            return DataResult.error(() -> "ConditionedOrigin: expected string or object");
        }

        @Override
        public <T> DataResult<T> encode(ConditionedOrigin input, DynamicOps<T> ops, T prefix) {
            if (input.condition.isEmpty()) {
                // Encode as plain string
                return DataResult.success(ops.createString(input.origin.toString()));
            }
            // Encode as object with condition
            JsonObject obj = new JsonObject();
            obj.addProperty("origin", input.origin.toString());
            obj.add("condition", input.condition.get().toJson());
            return DataResult.success(JsonOps.INSTANCE.convertTo(ops, obj));
        }
    };

    /**
     * Returns true if this origin is available given the player's current
     * layer choices. Origins without conditions are always available.
     *
     * @param chosenOrigins map of layer ID → chosen origin ID
     */
    public boolean isAvailable(Map<Identifier, Identifier> chosenOrigins) {
        return condition.map(c -> c.test(chosenOrigins)).orElse(true);
    }
}
