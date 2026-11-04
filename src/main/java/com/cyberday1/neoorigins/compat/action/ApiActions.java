package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.compat.condition.ApiConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Entity actions as the public API hands them out: plain {@code Consumer<ServerPlayer>}s. */
public final class ApiActions {

    private ApiActions() {}

    /** A parsed action that remembers its JSON, so the codec can encode it back. */
    record JsonAction(JsonElement source, EntityAction action) implements Consumer<ServerPlayer> {
        @Override public void accept(ServerPlayer player) { action.execute(player); }
    }

    /** An object, or an array of objects run in order. Unknown or malformed actions are logged and do nothing. */
    public static Consumer<ServerPlayer> parse(JsonElement json, String contextId) {
        JsonObject holder = new JsonObject();
        holder.add("a", json);
        EntityAction parsed = json != null && json.isJsonArray()
            ? ActionParser.parseField(holder, "a", contextId)
            : ActionParser.parse(json != null && json.isJsonObject() ? json.getAsJsonObject() : null, contextId);
        return new JsonAction(json == null ? null : json.deepCopy(), parsed);
    }

    public static final Codec<Consumer<ServerPlayer>> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Consumer<ServerPlayer>, T>> decode(DynamicOps<T> ops, T input) {
            JsonElement json;
            try {
                json = ops.convertTo(JsonOps.INSTANCE, input);
            } catch (Exception e) {
                return DataResult.error(() -> "action: could not convert to JSON: " + e.getMessage());
            }
            if (!json.isJsonObject() && !json.isJsonArray()) {
                return DataResult.error(() -> "action: expected an object or an array of objects, got " + json);
            }
            return DataResult.success(Pair.of(ApiActions.parse(json, ApiConditions.decodeContext()), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Consumer<ServerPlayer> input, DynamicOps<T> ops, T prefix) {
            if (input instanceof JsonAction ja && ja.source() != null) {
                return DataResult.success(JsonOps.INSTANCE.convertTo(ops, ja.source()));
            }
            return DataResult.error(() -> "action: only actions parsed from JSON can be encoded");
        }
    };
}
