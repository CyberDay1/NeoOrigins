package com.cyberday1.neoorigins.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side persistent storage for HUD layout: resource bar / ability
 * cluster positions plus the cluster's orientation, scale and split mode.
 * Positions are stored as screen-percentage (0.0–1.0) so they survive
 * resolution changes. Saved to {@code config/neoorigins/hud.json}.
 *
 * <p>File format (v2):
 * <pre>{@code
 * {
 *   "split_cooldown": false,
 *   "positions": {
 *     "<id>": { "xPct": 0.5, "yPct": 0.9, "scale": 1.0, "vertical": false }
 *   }
 * }
 * }</pre>
 * {@code scale} (0.5–2.0, default 1.0) and {@code vertical} (cluster
 * orientation, default false = horizontal) only carry meaning on the ability
 * cluster / split ability slots; resource bars ignore them. The legacy v1
 * format (a bare {@code id → {xPct,yPct}} map) is still read transparently.
 */
public final class ResourceHudPositions {

    /** Lower/upper bounds for the ability cluster / slot scale. */
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    public record Pos(float xPct, float yPct, float scale, boolean vertical) {

        /** Back-compat ctor for plain position-only callers (scale 1, horizontal). */
        public Pos(float xPct, float yPct) {
            this(xPct, yPct, 1.0f, false);
        }

        /**
         * Effective scale: Gson leaves {@code scale} at 0 when the field is
         * missing (legacy files), so anything out of range collapses to 1.
         */
        public float effScale() {
            return (scale >= MIN_SCALE && scale <= MAX_SCALE) ? scale : 1.0f;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("neoorigins").resolve("hud.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Pos>>() {}.getType();

    private static Map<String, Pos> positions = new HashMap<>();
    private static boolean splitCooldown = false;
    private static boolean loaded = false;

    public static Pos get(String resourceId) {
        ensureLoaded();
        return positions.get(resourceId);
    }

    public static void set(String resourceId, float xPct, float yPct) {
        ensureLoaded();
        Pos prev = positions.get(resourceId);
        positions.put(resourceId, new Pos(xPct, yPct,
            prev != null ? prev.scale() : 1.0f,
            prev != null && prev.vertical()));
    }

    /** Full-field setter used by the HUD editor (position + scale + orientation). */
    public static void set(String resourceId, Pos pos) {
        ensureLoaded();
        positions.put(resourceId, pos);
    }

    public static void remove(String resourceId) {
        ensureLoaded();
        positions.remove(resourceId);
    }

    /** Drops every stored entry whose id starts with {@code prefix} (split-slot reset). */
    public static void removeByPrefix(String prefix) {
        ensureLoaded();
        positions.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** True when each ability slot is its own independently placed HUD element. */
    public static boolean isSplitCooldown() {
        ensureLoaded();
        return splitCooldown;
    }

    public static void setSplitCooldown(boolean split) {
        ensureLoaded();
        splitCooldown = split;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                JsonObject root = new JsonObject();
                root.addProperty("split_cooldown", splitCooldown);
                root.add("positions", GSON.toJsonTree(positions, MAP_TYPE));
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            NeoOrigins.LOGGER.warn("Failed to save HUD positions: {}", e.getMessage());
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root == null || !root.isJsonObject()) return;
            JsonObject obj = root.getAsJsonObject();
            Map<String, Pos> read;
            if (obj.has("positions")) {
                // v2 — wrapper object with layout flags.
                splitCooldown = obj.has("split_cooldown") && obj.get("split_cooldown").getAsBoolean();
                read = GSON.fromJson(obj.get("positions"), MAP_TYPE);
            } else {
                // legacy v1 — the whole file is the position map.
                read = GSON.fromJson(obj, MAP_TYPE);
            }
            if (read != null) positions = new HashMap<>(read);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("Failed to load HUD positions: {}", e.getMessage());
        }
    }

    private ResourceHudPositions() {}
}
