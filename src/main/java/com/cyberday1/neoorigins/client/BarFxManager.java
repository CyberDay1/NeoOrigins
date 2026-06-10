package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry of animated resource-bar FX presets.
 *
 * <p>Presets are authored as resource-pack JSON under
 * {@code assets/<namespace>/bar_fx/<name>.json} and looked up by the
 * {@code animated} id a resource power declares in its {@code hud_render}
 * block (e.g. {@code "animated": "neoorigins:fire"}).
 *
 * <p>Because the entire bar render is client-side, FX presets live in
 * <em>assets</em> (resource packs), not datapacks — only the preset <em>id</em>
 * is synced from the server (a plain string on the resource entry); the client
 * resolves the id against its own loaded resource packs. A datapack author who
 * ships an animated bar therefore ships both the preset JSON and the texture in
 * the accompanying resource pack.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "texture": "neoorigins:textures/gui/bar_fx/fire.png",
 *   "mode": "scroll",          // only "scroll" supported today
 *   "tile_width": 213,          // source texture dimensions
 *   "tile_height": 16,
 *   "scroll_speed": 24,         // on-screen px/sec the strip drifts
 *   "track_color": "#AA2B0900", // dark backing under the EMPTY remainder (ARGB hex)
 *   "level_color": "#B3551500"  // backing under the FILLED portion (defaults to track_color)
 * }
 * }</pre>
 */
public class BarFxManager extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    public static final BarFxManager INSTANCE = new BarFxManager();

    // assets/<ns>/bar_fx/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("bar_fx");

    /** Replaced wholesale on reload; read from the render thread. */
    private static volatile Map<String, BarFx> PRESETS = Map.of();

    /**
     * A resolved FX preset.
     *
     * @param texture     the strip texture
     * @param scrollSpeed on-screen pixels/second the strip drifts left
     * @param trackColor  ARGB backing under the EMPTY remainder of the bar
     * @param levelColor  ARGB backing under the FILLED portion (behind the wisps) —
     *                    a brighter/saturated tone so the current level reads clearly
     *                    even in the transparent gaps between wisps
     * @param tileW       source texture width  (texels)
     * @param tileH       source texture height (texels)
     */
    public record BarFx(Identifier texture, float scrollSpeed, int trackColor, int levelColor, int tileW, int tileH) {}

    private BarFxManager() {}

    /** @return the preset for {@code id}, or {@code null} if none is loaded. */
    public static BarFx get(String id) {
        if (id == null || id.isBlank()) return null;
        return PRESETS.get(id);
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        for (var entry : FILE_CONVERTER.listMatchingResources(rm).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier id = FILE_CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                map.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("[bar_fx] Error reading FX preset file {}", fileId, e);
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, BarFx> loaded = new HashMap<>();
        for (var e : entries.entrySet()) {
            Identifier id = e.getKey();
            try {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject obj = e.getValue().getAsJsonObject();

                Identifier texture = Identifier.parse(obj.get("texture").getAsString());
                float scrollSpeed = obj.has("scroll_speed") ? obj.get("scroll_speed").getAsFloat() : 24.0f;
                int tileW = obj.has("tile_width") ? obj.get("tile_width").getAsInt() : 64;
                int tileH = obj.has("tile_height") ? obj.get("tile_height").getAsInt() : 8;
                int trackColor = obj.has("track_color")
                    ? parseArgb(obj.get("track_color").getAsString()) : 0xAA000000;
                // Backing under the filled portion; defaults to the track color so a
                // preset that omits it behaves exactly as before.
                int levelColor = obj.has("level_color")
                    ? parseArgb(obj.get("level_color").getAsString()) : trackColor;

                loaded.put(id.toString(), new BarFx(texture, scrollSpeed, trackColor, levelColor, Math.max(1, tileW), Math.max(1, tileH)));
            } catch (Exception ex) {
                NeoOrigins.LOGGER.warn("[bar_fx] Skipping malformed FX preset {}: {}", id, ex.getMessage());
            }
        }
        PRESETS = ImmutableMap.copyOf(loaded);
        NeoOrigins.LOGGER.info("[bar_fx] Loaded {} animated resource-bar preset(s)", loaded.size());
    }

    /** Parse a hex color string ({@code RRGGBB} or {@code AARRGGBB}, optional leading #). */
    private static int parseArgb(String s) {
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) s = "FF" + s;   // assume opaque when no alpha given
        return (int) Long.parseLong(s, 16);
    }
}
