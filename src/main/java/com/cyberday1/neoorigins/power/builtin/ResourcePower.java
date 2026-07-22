package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.ResourceBackingRouter;
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

    /** Tracks previous resource values for edge-triggered min/max actions. */
    private static final java.util.Map<String, Integer> PREV_VALUES = new java.util.concurrent.ConcurrentHashMap<>();

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
        boolean hidden,
        String animated,
        int tint,
        boolean alwaysShow,
        String type,
        String backing
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
                // Optional external backing: when set to "irons_spellbooks:mana"
                // the bar's value is read from / written to the player's Iron's
                // Spells mana pool (additive-only) instead of the internal
                // ResourceState store. Empty/absent = internally stored.
                String backing = obj.has("backing") ? obj.get("backing").getAsString() : "";

                EntityCondition regenCond = ConditionParser.parseField(obj, "regen_condition", t);
                EntityAction minAction = ActionParser.parseField(obj, "min_action", t);
                EntityAction maxAction = ActionParser.parseField(obj, "max_action", t);

                // HUD render
                String label = "Resource";
                int color = 0xFF55AAFF;
                String animated = "";
                int tint = 0;
                boolean hidden = obj.has("hidden") && obj.get("hidden").getAsBoolean();
                boolean alwaysShow = false;
                if (obj.has("hud_render") && obj.get("hud_render").isJsonObject()) {
                    JsonObject hud = obj.getAsJsonObject("hud_render");
                    if (hud.has("label")) label = hud.get("label").getAsString();
                    if (hud.has("color")) {
                        String cs = hud.get("color").getAsString();
                        color = parseColor(cs);
                    }
                    // Animated FX preset id (client resolves it against bar_fx/ presets);
                    // optional tint multiplies the preset art so one strip can be recoloured.
                    if (hud.has("animated")) animated = hud.get("animated").getAsString();
                    if (hud.has("tint")) tint = parseColor(hud.get("tint").getAsString());
                    // Origins compat: should_render=false hides the bar
                    if (hud.has("should_render") && !hud.get("should_render").getAsBoolean()) {
                        hidden = true;
                    }
                    // Opt-in: keep the bar on-screen even at full value. By default the
                    // HUD hides a full bar (Apoli convention); a regenerating meter that
                    // sits at max is then invisible, so authors can force it visible.
                    if (hud.has("always_render")) alwaysShow = hud.get("always_render").getAsBoolean();
                }

                return DataResult.success(Pair.of(new Config(
                    powerId, min, max, startValue, regenRate, regenInterval,
                    regenCond, minAction, maxAction, label, color, hidden, animated, tint, alwaysShow, t, backing
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
        if (ContentTogglesConfig.isResourceBarsDisabled()) return;
        String key = storageKey(player, config);
        CompatAttachments.registerResourceBacking(key, config.backing());
        // A mana-backed bar has no internal store to seed — its value lives in
        // the Iron's pool, which is authoritative. Only seed start_value for
        // internally-stored resources.
        if (!CompatAttachments.isManaBacked(key)) {
            player.getData(CompatAttachments.resourceState()).set(key, config.startValue());
        }
        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(config.min(), config.max(), config.label(), config.color(),
                config.hidden(), config.animated(), config.tint(), config.alwaysShow()));
        CompatAttachments.syncResourcesToClient(player);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        String key = storageKey(player, config);
        player.getData(CompatAttachments.resourceState()).remove(key);
        CompatAttachments.unregisterResourceMeta(key);
        CompatAttachments.unregisterResourceBacking(key);
        CompatAttachments.syncResourcesToClient(player);
        PREV_VALUES.remove(player.getUUID() + ":" + key);
    }

    @Override
    public void onLogin(ServerPlayer player, Config config) {
        if (ContentTogglesConfig.isResourceBarsDisabled()) return;
        String key = storageKey(player, config);
        CompatAttachments.registerResourceBacking(key, config.backing());
        // Restore resource meta on relog WITHOUT touching a stored value.
        // The base PowerType.onLogin default delegates to onGranted, but
        // onGranted resets the stored resource to config.startValue() — so a
        // returning player's energy/stamina was being wiped back to start on
        // every relog. This override re-registers the meta and re-syncs the
        // client (so the bar renders) while leaving the persisted value in
        // the attachment untouched. GitHub #90.
        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(config.min(), config.max(), config.label(), config.color(),
                config.hidden(), config.animated(), config.tint(), config.alwaysShow()));
        // Seed the state attachment if it has no entry for this key. Hits two
        // cases: (a) players granted this power before resource_state shipped,
        // who never had their state seeded by onGranted; (b) a state map that
        // failed to round-trip a particular key. Without a state entry,
        // syncResourcesToClient iterates state.getAll() and skips the bar
        // entirely — symptom is "energy bar disappeared, abilities won't fire
        // because cur=0". GitHub #90 follow-up to f1c492fe.
        // A mana-backed bar has no internal store — skip seeding entirely; its
        // value is read live from the Iron's pool by the sync/read paths.
        if (!CompatAttachments.isManaBacked(key)) {
            var state = player.getData(CompatAttachments.resourceState());
            if (!state.has(key)) {
                state.set(key, config.startValue());
            }
        }
        CompatAttachments.syncResourcesToClient(player);
    }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (ContentTogglesConfig.isResourceBarsDisabled()) return;
        String key = storageKey(player, config);
        boolean manaBacked = CompatAttachments.isManaBacked(key);
        var state = player.getData(CompatAttachments.resourceState());

        // Regen
        if (config.regenRate() != 0 && player.tickCount % config.regenInterval() == 0) {
            if (config.regenCondition().test(player)) {
                if (manaBacked) {
                    // Additive grant into the Iron's pool (Iron's clamps the top).
                    ResourceBackingRouter.add(player, key, config.regenRate());
                } else {
                    state.clampedAdd(key, config.regenRate(), config.min(), config.max());
                }
            }
        }

        // Threshold actions — edge-triggered: only fire when the value
        // transitions TO the boundary, not every tick while sitting on it. For a
        // mana-backed bar the current value is the live pool reading.
        int cur = manaBacked
            ? ResourceBackingRouter.read(player, key, config.min())
            : state.get(key, config.startValue());
        String edgeKey = player.getUUID() + ":" + key;
        Integer prev = PREV_VALUES.put(edgeKey, cur);
        if (prev != null) {
            if (cur <= config.min() && prev > config.min()) config.minAction().execute(player);
            if (cur >= config.max() && prev < config.max()) config.maxAction().execute(player);
        }

        if (manaBacked) {
            // The Iron's pool changes out from under us (regen, spellcasting), so
            // the internal dirty flag never trips. Push the value on the same
            // 10-tick cadence so the bar tracks the live pool.
            if (player.tickCount % 10 == 0) {
                CompatAttachments.syncResourceValuesToClient(player);
            }
        } else if (state.isDirty() && player.tickCount % 10 == 0) {
            // Sync to client every 10 ticks when dirty — value-only payload; the
            // static display metadata was already pushed by the full sync at
            // grant/login/origin change.
            state.clearDirty();
            CompatAttachments.syncResourceValuesToClient(player);
        }
    }

    // --- Public API for resource_cost deduction ---

    /** Get the current value of a resource by power ID. */
    public static int getValue(ServerPlayer player, String key) {
        // Mana-backed resources read live from the Iron's pool (authoritative);
        // fall back to the bar's min (or 0 when unknown) when Iron's is absent.
        if (CompatAttachments.isManaBacked(key)) {
            var meta = CompatAttachments.getResourceMeta(key);
            return ResourceBackingRouter.read(player, key, meta != null ? meta.min() : 0);
        }
        return player.getData(CompatAttachments.resourceState()).get(key, 0);
    }

    public static void clearPlayer(java.util.UUID uuid) {
        String prefix = uuid.toString() + ":";
        PREV_VALUES.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Deduct amount from a resource. Returns true if sufficient, false if not. */
    public static boolean deduct(ServerPlayer player, String key, int amount) {
        // Mana-backed: gate on the live pool, then add a negative delta (the
        // router floor-clamps so mana can't go below 0). No internal store.
        if (CompatAttachments.isManaBacked(key)) {
            var meta = CompatAttachments.getResourceMeta(key);
            int cur = ResourceBackingRouter.read(player, key, meta != null ? meta.min() : 0);
            if (cur < amount) return false;
            ResourceBackingRouter.add(player, key, -amount);
            return true;
        }
        var state = player.getData(CompatAttachments.resourceState());
        var meta = CompatAttachments.getResourceMeta(key);
        int min = meta != null ? meta.min() : 0;
        int cur = state.get(key, 0);
        if (cur < amount) return false;
        state.clampedAdd(key, -amount, min, Integer.MAX_VALUE);
        return true;
    }
}
