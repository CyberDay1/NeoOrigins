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
import java.util.Set;

/**
 * Tints the player model with an RGBA colour. Client-side rendering is
 * handled by {@code VisualEffectsHandler} which reads the encoded colour
 * from the capability tag.
 *
 * <p>An optional {@code condition} field gates when the colour is applied.
 * When present, the tint only shows while the condition evaluates true
 * (e.g. low health, on fire, in a specific biome). When absent, the tint
 * is always active.
 *
 * <pre>{@code
 * {
 *   "type": "neoorigins:model_color",
 *   "red": 0.8, "green": 0.2, "blue": 0.2, "alpha": 0.6,
 *   "condition": { "type": "neoorigins:health", "comparison": "<=", "value": 6 }
 * }
 * }</pre>
 */
public class ModelColorPower extends PowerType<ModelColorPower.Config> {

    /** Tracks previous condition result per-player to avoid redundant resyncs. */
    private static final java.util.Map<java.util.UUID, Boolean> CONDITION_STATE = new java.util.concurrent.ConcurrentHashMap<>();

    public record Config(
        float red, float green, float blue, float alpha,
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
                    return DataResult.error(() -> "model_color: JSON conversion failed: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "model_color: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:model_color";
                float r = obj.has("red")   ? obj.get("red").getAsFloat()   : 1.0f;
                float g = obj.has("green") ? obj.get("green").getAsFloat() : 1.0f;
                float b = obj.has("blue")  ? obj.get("blue").getAsFloat()  : 1.0f;
                float a = obj.has("alpha") ? obj.get("alpha").getAsFloat() : 1.0f;

                Optional<EntityCondition> cond = obj.has("condition")
                    ? Optional.of(ConditionParser.parseField(obj, "condition", t))
                    : Optional.empty();

                return DataResult.success(Pair.of(new Config(r, g, b, a, cond, t), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public Set<String> capabilities(Config config) {
        // If there's a condition, don't emit statically — the player-aware
        // variant handles it.
        if (config.condition().isPresent()) return Set.of();
        return colorCapSet(config);
    }

    @Override
    public Set<String> capabilities(ServerPlayer player, Config config) {
        if (config.condition().isPresent() && !config.condition().get().test(player)) {
            return Set.of();
        }
        return colorCapSet(config);
    }

    /**
     * Re-evaluate the condition every second and resync capabilities to the
     * client when the result changes — otherwise the tint only updates on
     * toggle or login.
     */
    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (config.condition().isEmpty()) return;
        if (player.tickCount % 20 != 0) return;

        boolean active = config.condition().get().test(player);
        Boolean prev = CONDITION_STATE.put(player.getUUID(), active);
        if (prev == null || prev != active) {
            com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncActivePowersToPlayer(player);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        CONDITION_STATE.remove(player.getUUID());
    }

    public static void clearPlayer(java.util.UUID uuid) {
        CONDITION_STATE.remove(uuid);
    }

    private static Set<String> colorCapSet(Config config) {
        return Set.of("model_color:" + config.red() + ":" + config.green()
            + ":" + config.blue() + ":" + config.alpha());
    }
}
