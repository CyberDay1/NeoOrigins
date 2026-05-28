package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Loads {@link UITheme} definitions from {@code assets/<ns>/ui_themes/*.json}.
 * The active theme defaults to {@code neoorigins:parchment}. A resource pack
 * can override that file to retheme the selection / info screens without code.
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

    public UIThemeManager() {
        super(GSON, "ui_themes");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
        JsonElement el = map.get(DEFAULT_ID);
        if (el == null || !el.isJsonObject()) {
            UITheme.setCurrent(UITheme.PARCHMENT);
            return;
        }
        try {
            UITheme.setCurrent(parse(el.getAsJsonObject()));
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("Failed to load UI theme {} — falling back to parchment default", DEFAULT_ID, e);
            UITheme.setCurrent(UITheme.PARCHMENT);
        }
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
