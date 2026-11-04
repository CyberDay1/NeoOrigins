package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Entity conditions as the public API hands them out: plain {@code Predicate<ServerPlayer>}s. */
public final class ApiConditions {

    private ApiConditions() {}

    /** Context id (the power being loaded) for codec decodes, so a failed condition names its power. */
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    private static final String DEFAULT_CONTEXT = "addon power config";

    /** A parsed condition that remembers its JSON, so the codec can encode it back. */
    record JsonCondition(JsonElement source, EntityCondition condition) implements Predicate<ServerPlayer> {
        @Override public boolean test(ServerPlayer player) { return condition.test(player); }
    }

    /** An object, or an array of objects that must all pass. Unknown or malformed conditions fail closed, logged. */
    public static Predicate<ServerPlayer> parse(JsonElement json, String contextId) {
        JsonObject holder = new JsonObject();
        holder.add("c", json);
        EntityCondition parsed = json != null && json.isJsonArray()
            ? ConditionParser.parseField(holder, "c", contextId)
            : ConditionParser.parse(json != null && json.isJsonObject() ? json.getAsJsonObject() : null, contextId);
        return new JsonCondition(json == null ? null : json.deepCopy(), parsed);
    }

    /** The internal form of a public predicate, unwrapping parsed ones. */
    public static EntityCondition toEntityCondition(Predicate<ServerPlayer> predicate) {
        if (predicate == null) return null;
        if (predicate instanceof JsonCondition jc) return jc.condition();
        return predicate::test;
    }

    /** The log context for a codec decode: the power being loaded, or a generic label. */
    public static String decodeContext() {
        String ctx = CONTEXT.get();
        return ctx != null ? ctx : DEFAULT_CONTEXT;
    }

    /** Runs {@code body} with {@code contextId} as the log context of any condition or action the codec decodes. */
    public static <T> T withContext(String contextId, Supplier<T> body) {
        String prev = CONTEXT.get();
        CONTEXT.set(contextId);
        try { return body.get(); }
        finally { if (prev == null) CONTEXT.remove(); else CONTEXT.set(prev); }
    }

    public static final Codec<Predicate<ServerPlayer>> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Predicate<ServerPlayer>, T>> decode(DynamicOps<T> ops, T input) {
            JsonElement json;
            try {
                json = ops.convertTo(JsonOps.INSTANCE, input);
            } catch (Exception e) {
                return DataResult.error(() -> "condition: could not convert to JSON: " + e.getMessage());
            }
            if (!json.isJsonObject() && !json.isJsonArray()) {
                return DataResult.error(() -> "condition: expected an object or an array of objects, got " + json);
            }
            return DataResult.success(Pair.of(ApiConditions.parse(json, decodeContext()), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Predicate<ServerPlayer> input, DynamicOps<T> ops, T prefix) {
            if (input instanceof JsonCondition jc && jc.source() != null) {
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, jc.source()));
            }
            return DataResult.error(() -> "condition: only conditions parsed from JSON can be encoded");
        }
    };
}
