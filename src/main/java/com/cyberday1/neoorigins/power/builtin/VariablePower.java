package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;

/**
 * A named, persistent, ALWAYS-HIDDEN integer counter ("local variable").
 *
 * <p>Unlike {@link ResourcePower}, a variable has no HUD bar, no regen, and no
 * per-tick cost — it only changes when an action explicitly touches it
 * ({@code change_resource} / {@code set_resource}). It is read as a gate by the
 * {@code resource} condition, so the same authoring surface drives both passive
 * and active abilities.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:variable",
 *   "start": 0,
 *   "min": 0,
 *   "max": 99
 * }
 * }</pre>
 *
 * <p>The counter's storage key is the declaring power's own id (injected as
 * {@code _power_id}), so variables share the {@link CompatAttachments.ResourceState}
 * keyspace with resources — {@code change_resource} and the {@code resource}
 * condition operate on them with no extra wiring. Because two powers can never
 * share an id, a variable can never collide with a resource name.
 *
 * <p>{@code min}/{@code max} clamp additive writes; omit them for an unbounded
 * counter. {@code start} is the value seeded on grant AND the value reads fall
 * back to before the first write — see
 * {@link CompatAttachments#variableStart(String)}. Declarations are registered
 * at power-load time (see {@code PowerDataManager}) so a read resolves the
 * declared start regardless of where the variable sits in the origin's power
 * list ("declared at the start of the power stack").
 */
public class VariablePower extends PowerType<VariablePower.Config> {

    public record Config(String powerId, int start, int min, int max) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "variable: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "variable: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String powerId = obj.has("_power_id") ? obj.get("_power_id").getAsString() : "neoorigins:variable";
                int min = obj.has("min") ? obj.get("min").getAsInt() : Integer.MIN_VALUE;
                int max = obj.has("max") ? obj.get("max").getAsInt() : Integer.MAX_VALUE;
                int start = obj.has("start") ? obj.get("start").getAsInt()
                          : obj.has("start_value") ? obj.get("start_value").getAsInt()
                          : 0;
                int clampedStart = Math.max(min, Math.min(max, start));
                return DataResult.success(Pair.of(new Config(powerId, clampedStart, min, max), ops.empty()));
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
    public void onGranted(ServerPlayer player, Config config) {
        // Register the declaration defensively (idempotent with the load-time
        // registration in PowerDataManager) and seed the stored value. A fresh
        // grant resets the counter to its start value.
        CompatAttachments.registerVariable(config.powerId(),
            new CompatAttachments.VariableDecl(config.start(), config.min(), config.max()));
        player.getData(CompatAttachments.resourceState()).set(config.powerId(), config.start());
    }

    @Override
    public void onLogin(ServerPlayer player, Config config) {
        // Saved between login sessions: re-register the declaration but leave a
        // persisted value untouched. Only seed when the player has no stored
        // entry yet (first login after the variable was added to their origin).
        CompatAttachments.registerVariable(config.powerId(),
            new CompatAttachments.VariableDecl(config.start(), config.min(), config.max()));
        var state = player.getData(CompatAttachments.resourceState());
        if (!state.has(config.powerId())) {
            state.set(config.powerId(), config.start());
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.getData(CompatAttachments.resourceState()).remove(config.powerId());
    }
}
