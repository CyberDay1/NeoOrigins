package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link UITheme} definitions from {@code assets/<ns>/ui_themes/*.json}.
 *
 * <p>Every JSON file under that path is parsed into a selectable theme keyed by
 * its {@link ResourceLocation} id ({@code <ns>:<file_stem>}). The mod ships a
 * built-in {@code neoorigins:parchment} theme that's always present even when
 * no JSON sits on disk for it — addon packs that want to swap the panel,
 * colours, or font just drop a new file under their own namespace.
 *
 * <p>The active theme (which one the screens actually render) is decided by
 * {@link ActiveThemeRegistry} — it composes the datapack-declared selection
 * from {@link com.cyberday1.neoorigins.data.ActiveThemeManager} with the
 * client-side {@code ui.theme_override} from
 * {@link com.cyberday1.neoorigins.client.NeoOriginsClientConfig}, falling back
 * to {@link #DEFAULT_ID} when neither resolves.
 *
 * <p>JSON schema (all fields optional — missing fields keep the parchment
 * default):
 * <pre>{@code
 * {
 *   "panel_background": "neoorigins:textures/gui/themes/parchment/panel.png",
 *   "overlay_color":          "0xCC060610",
 *   "name_color":             "0xFF2A1810",
 *   "description_color":      "0xFF3A2410",
 *   "power_name_color":       "0xFF6B3B10",
 *   "power_description_color":"0xFF4A2A10",
 *   "header_color":           "0xFF2A1810",
 *   "border_color":           "0xFF6B4A20",
 *   "muted_color":            "0xFF4A2A10",
 *   "accent_color":           "0xFFB87328",
 *   "font": "neoorigins:parchment",
 *   "inset_left": 12, "inset_top": 12, "inset_right": 12, "inset_bottom": 12,
 *   "texture_width": 256, "texture_height": 256
 * }
 * }</pre>
 */
public class UIThemeManager extends SimpleJsonResourceReloadListener {

    public static final UIThemeManager INSTANCE = new UIThemeManager();
    public static final ResourceLocation DEFAULT_ID =
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "parchment");

    private static final Gson GSON = new Gson();

    /** Loaded themes keyed by {@code <ns>:<file_stem>}. Populated on each reload. */
    private static volatile Map<ResourceLocation, UITheme> THEMES = Map.of(DEFAULT_ID, UITheme.PARCHMENT);

    public UIThemeManager() {
        super(GSON, "ui_themes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, UITheme> loaded = new HashMap<>();
        // Always seed the built-in default — a pack can still override it by
        // shipping its own neoorigins:parchment JSON, which replaces this entry
        // via the map.put below.
        loaded.put(DEFAULT_ID, UITheme.PARCHMENT);

        for (var entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement el = entry.getValue();
            if (el == null || !el.isJsonObject()) continue;
            try {
                loaded.put(id, parse(el.getAsJsonObject()));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Failed to load UI theme {} — skipping", id, e);
            }
        }

        THEMES = Map.copyOf(loaded);
        NeoOrigins.LOGGER.debug("Loaded {} UI theme(s): {}", THEMES.size(), THEMES.keySet());

        // Resolve and apply the active theme so a /reload picks up newly-loaded
        // (or removed) themes immediately.
        ActiveThemeRegistry.reapply();
    }

    /** All loaded themes keyed by id. Snapshot map; safe to iterate. */
    public static Map<ResourceLocation, UITheme> themes() {
        return THEMES;
    }

    /** The theme registered under {@code id}, or {@code null} if no such theme is loaded. */
    public static UITheme get(ResourceLocation id) {
        return THEMES.get(id);
    }

    /** Convenience: theme by id, falling back to the built-in parchment when absent. */
    public static UITheme getOrDefault(ResourceLocation id) {
        if (id == null) return UITheme.PARCHMENT;
        UITheme t = THEMES.get(id);
        return t != null ? t : UITheme.PARCHMENT;
    }

    private static UITheme parse(JsonObject o) {
        UITheme d = UITheme.PARCHMENT;
        return new UITheme(
            res(o, "panel_background", d.panelBackground()),
            argb(o, "overlay_color",           d.overlayColor()),
            argb(o, "name_color",              d.nameColor()),
            argb(o, "description_color",       d.descriptionColor()),
            argb(o, "power_name_color",        d.powerNameColor()),
            argb(o, "power_description_color", d.powerDescriptionColor()),
            argb(o, "header_color",            d.headerColor()),
            argb(o, "border_color",            d.borderColor()),
            argb(o, "muted_color",             d.mutedColor()),
            argb(o, "accent_color",            d.accentColor()),
            res(o, "font", d.font()),
            intOr(o, "inset_left",   d.insetLeft()),
            intOr(o, "inset_top",    d.insetTop()),
            intOr(o, "inset_right",  d.insetRight()),
            intOr(o, "inset_bottom", d.insetBottom()),
            intOr(o, "texture_width",  d.textureWidth()),
            intOr(o, "texture_height", d.textureHeight())
        );
    }

    private static ResourceLocation res(JsonObject o, String k, ResourceLocation fallback) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive()) return fallback;
        ResourceLocation rl = ResourceLocation.tryParse(o.get(k).getAsString());
        return rl != null ? rl : fallback;
    }

    private static int argb(JsonObject o, String k, int fallback) {
        if (!o.has(k)) return fallback;
        JsonElement el = o.get(k);
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt();
            String s = el.getAsString().trim();
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            else if (s.startsWith("#")) s = s.substring(1);
            try {
                return (int) Long.parseLong(s, 16);
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static int intOr(JsonObject o, String k, int fallback) {
        if (o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsJsonPrimitive().isNumber()) {
            return o.get(k).getAsInt();
        }
        return fallback;
    }
}
