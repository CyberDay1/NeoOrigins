package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatAttachments;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A named, persistent, HUD-visible resource bar.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "neoorigins:resource",
 *   "min": 0,
 *   "max": 100,
 *   "start_value": 100,
 *   "regen_rate": 1,
 *   "regen_interval": 20,
 *   "regen_condition": { ... optional EntityCondition ... },
 *   "min_action": { ... optional EntityAction ... },
 *   "max_action": { ... optional EntityAction ... },
 *   "hud_render": {
 *     "label": "Mana",
 *     "color": "#55AAFF"
 *   }
 * }
 * }</pre>
 *
 * <p>Values are stored in {@link CompatAttachments.ResourceState} as integers
 * keyed by the power's ResourceLocation (same storage as compat-layer resources,
 * so {@code change_resource} and {@code resource} conditions work uniformly).
 */
public class ResourcePower extends PowerType<ResourcePower.Config> {

    public record Config(
        String powerId,
        int min,
        int max,
        int startValue,
        int regenRate,
        int regenInterval,
        EntityCondition regenCondition,
        EntityAction minAction,
        EntityAction maxAction,
        String label,
        int color,
        String type
    ) implements PowerConfiguration {

        public static final Codec<Config> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Config, T>> decode(DynamicOps<T> ops, T input) {
                JsonElement json;
                try {
                    json = ops.convertTo(JsonOps.INSTANCE, input);
                } catch (Exception e) {
                    return DataResult.error(() -> "resource: could not convert to JSON: " + e.getMessage());
                }
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "resource: expected JSON object");
                }
                JsonObject obj = json.getAsJsonObject();
                String powerId = obj.has("_power_id") ? obj.get("_power_id").getAsString() : "neoorigins:resource";
                int min = obj.has("min") ? obj.get("min").getAsInt() : 0;
                int max = obj.has("max") ? obj.get("max").getAsInt() : 100;
                int startValue = obj.has("start_value") ? obj.get("start_value").getAsInt() : max;
                int regenRate = obj.has("regen_rate") ? obj.get("regen_rate").getAsInt() : 0;
                int regenInterval = Math.max(1, obj.has("regen_interval") ? obj.get("regen_interval").getAsInt() : 20);
                String t = obj.has("type") ? obj.get("type").getAsString() : "neoorigins:resource";

                EntityCondition regenCond = obj.has("regen_condition") && obj.get("regen_condition").isJsonObject()
                    ? ConditionParser.parse(obj.getAsJsonObject("regen_condition"), t) : EntityCondition.alwaysTrue();
                EntityAction minAction = obj.has("min_action") && obj.get("min_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("min_action"), t) : EntityAction.noop();
                EntityAction maxAction = obj.has("max_action") && obj.get("max_action").isJsonObject()
                    ? ActionParser.parse(obj.getAsJsonObject("max_action"), t) : EntityAction.noop();

                // HUD render
                String label = "Resource";
                int color = 0xFF55AAFF;
                if (obj.has("hud_render") && obj.get("hud_render").isJsonObject()) {
                    JsonObject hud = obj.getAsJsonObject("hud_render");
                    if (hud.has("label")) label = hud.get("label").getAsString();
                    if (hud.has("color")) {
                        String cs = hud.get("color").getAsString();
                        color = parseColor(cs);
                    }
                }

                return DataResult.success(Pair.of(new Config(
                    powerId, min, max, startValue, regenRate, regenInterval,
                    regenCond, minAction, maxAction, label, color, t
                ), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Config input, DynamicOps<T> ops, T prefix) {
                return DataResult.success(prefix);
            }
        };

        private static int parseColor(String s) {
            try {
                if (s.startsWith("#")) s = s.substring(1);
                if (s.length() == 6) s = "FF" + s; // add alpha
                return (int) Long.parseLong(s, 16);
            } catch (NumberFormatException e) {
                return 0xFF55AAFF;
            }
        }
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    private static String storageKey(ServerPlayer player, Config config) {
        return config.powerId();
    }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        String key = storageKey(player, config);
        player.getData(CompatAttachments.resourceState()).set(key, config.startValue());
        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(config.min(), config.max(), config.label(), config.color()));
        CompatAttachments.syncResourcesToClient(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        String key = storageKey(player, config);
        player.getData(CompatAttachments.resourceState()).set(key, 0);
        CompatAttachments.syncResourcesToClient(player);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        String key = storageKey(player, config);
        var state = player.getData(CompatAttachments.resourceState());

        // Regen
        if (config.regenRate() != 0 && player.tickCount % config.regenInterval() == 0) {
            if (config.regenCondition().test(player)) {
                state.clampedAdd(key, config.regenRate(), config.min(), config.max());
            }
        }

        // Threshold actions
        int cur = state.get(key, config.startValue());
        if (cur <= config.min()) config.minAction().execute(player);
        if (cur >= config.max()) config.maxAction().execute(player);

        // Sync to client every 10 ticks when dirty
        if (state.isDirty() && player.tickCount % 10 == 0) {
            state.clearDirty();
            CompatAttachments.syncResourcesToClient(player);
        }
    }

    // --- Public API for resource_cost deduction ---

    /** Get the current value of a resource by power ID. */
    public static int getValue(ServerPlayer player, String key) {
        return player.getData(CompatAttachments.resourceState()).get(key, 0);
    }

    /** Deduct amount from a resource. Returns true if sufficient, false if not. */
    public static boolean deduct(ServerPlayer player, String key, int amount) {
        var state = player.getData(CompatAttachments.resourceState());
        var meta = CompatAttachments.getResourceMeta(key);
        int min = meta != null ? meta.min() : 0;
        int cur = state.get(key, 0);
        if (cur < amount) return false;
        state.clampedAdd(key, -amount, min, Integer.MAX_VALUE);
        return true;
    }
}
