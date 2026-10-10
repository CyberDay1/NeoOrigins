package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.util.HexColor;
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
 *   "mode": "scroll",          // "scroll" (default) or "static"
 *   "tile_width": 126,          // source texture dimensions
 *   "tile_height": 6,
 *   "loop_width": 125,          // texels per scroll loop; defaults to tile_width
 *   "scroll_speed": 16,         // px/sec the strip drifts left (negative = right)
 *   "frame": "neoorigins:textures/gui/bar_fx/empty_bar.png", // optional framed layout
 *   "frame_width": 71,          // frame size; the fill window is the frame inset 1px
 *   "frame_height": 8,
 *   "tint_from_color": false,   // tint with hud_render.color when no tint is given
 *   "track_color": "#AA2B0900", // unframed only: backing under the EMPTY remainder
 *   "level_color": "#B3551500"  // unframed only: backing under the FILLED portion
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
     * @param loopW       texels per scroll loop (a strip whose last column repeats
     *                    its first loops one texel short of its width)
     * @param scrolls     false for {@code "mode": "static"}: the fill is drawn unmoving
     * @param frame       optional frame texture; non-null selects the framed layout
     * @param frameW      frame width  (texels); the fill window is the frame inset 1px
     * @param frameH      frame height (texels)
     * @param tintFromColor when the power sets no {@code tint}, multiply by its {@code color}
     */
    public record BarFx(Identifier texture, float scrollSpeed, int trackColor, int levelColor, int tileW, int tileH,
                        int loopW, boolean scrolls, Identifier frame, int frameW, int frameH, boolean tintFromColor) {}

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
                    ? parseArgb("track_color", obj.get("track_color").getAsString()) : 0xAA000000;
                // Backing under the filled portion; defaults to the track color so a
                // preset that omits it behaves exactly as before.
                int levelColor = obj.has("level_color")
                    ? parseArgb("level_color", obj.get("level_color").getAsString()) : trackColor;

                tileW = Math.max(1, tileW);
                tileH = Math.max(1, tileH);
                int loopW = obj.has("loop_width") ? obj.get("loop_width").getAsInt() : tileW;
                loopW = Math.max(1, Math.min(tileW, loopW));
                String mode = obj.has("mode") ? obj.get("mode").getAsString() : "scroll";
                boolean scrolls = !"static".equals(mode);
                Identifier frame = obj.has("frame") ? Identifier.parse(obj.get("frame").getAsString()) : null;
                int frameW = obj.has("frame_width") ? obj.get("frame_width").getAsInt() : 71;
                int frameH = obj.has("frame_height") ? obj.get("frame_height").getAsInt() : 8;
                boolean tintFromColor = obj.has("tint_from_color") && obj.get("tint_from_color").getAsBoolean();

                loaded.put(id.toString(), new BarFx(texture, scrollSpeed, trackColor, levelColor, tileW, tileH,
                    loopW, scrolls, frame, Math.max(3, frameW), Math.max(3, frameH), tintFromColor));
            } catch (Exception ex) {
                NeoOrigins.LOGGER.warn("[bar_fx] Skipping malformed FX preset {}: {}", id, ex.getMessage());
            }
        }
        PRESETS = ImmutableMap.copyOf(loaded);
        NeoOrigins.LOGGER.info("[bar_fx] Loaded {} animated resource-bar preset(s)", loaded.size());
    }

    /** Parse a hex color string ({@code RRGGBB} or {@code AARRGGBB}, optional leading #); anything else skips the preset. */
    static int parseArgb(String field, String s) {
        Integer c = HexColor.parse(s);
        if (c == null) throw new IllegalArgumentException(field + " \"" + s + "\" is not a 6- or 8-digit hex colour");
        return c;
    }
}
