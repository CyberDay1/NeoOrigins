package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side reload listener that resolves the datapack-declared "active" UI
 * theme. Pack authors drop a tiny JSON at
 * {@code data/<ns>/neoorigins/active_theme.json} containing:
 *
 * <pre>{@code
 *   { "theme": "<ns>:<id>" }
 * }</pre>
 *
 * <p>The selected id is broadcast to every player on login + on each datapack
 * sync via {@code SyncActiveThemePayload}. Clients then map it to a loaded
 * {@link com.cyberday1.neoorigins.client.theme.UITheme} (client-side override
 * takes precedence — see {@code ActiveThemeRegistry}).
 *
 * <p><b>Conflict handling:</b> if multiple addon packs each declare an
 * {@code active_theme.json}, the entry that loads last wins. A warning is
 * logged listing every contributing pack so authors can see the collision and
 * adjust load order (or just accept that the last-loaded pack's theme is the
 * one used). Pack load order is determined by Minecraft's resource pack
 * ordering — usually alphabetical within a tier.
 */
public class ActiveThemeManager extends SimpleJsonResourceReloadListener {

    public static final ActiveThemeManager INSTANCE = new ActiveThemeManager();
    private static final Gson GSON = new Gson();

    /**
     * The fixed id under each namespace where we look. Combined with the
     * {@code "neoorigins"} folder argument below, this resolves to
     * {@code data/<ns>/neoorigins/active_theme.json}.
     */
    private static final String FILE_PATH = "active_theme";

    /** Resolved selection — {@code null} if no pack declared one. */
    private volatile ResourceLocation selected;

    public ActiveThemeManager() {
        super(GSON, "neoorigins");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller profiler) {
        // We only care about entries whose file stem is exactly "active_theme".
        // The folder scan also turns up other neoorigins:* data files; ignore them.
        List<Declaration> declarations = new ArrayList<>();
        for (var entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!FILE_PATH.equals(id.getPath())) continue;
            JsonElement el = entry.getValue();
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("theme") || !obj.get("theme").isJsonPrimitive()) {
                NeoOrigins.LOGGER.warn(
                    "[theming] active_theme.json from namespace '{}' is missing a 'theme' string — skipping",
                    id.getNamespace());
                continue;
            }
            String raw = obj.get("theme").getAsString();
            ResourceLocation themeId = ResourceLocation.tryParse(raw);
            if (themeId == null) {
                NeoOrigins.LOGGER.warn(
                    "[theming] active_theme.json from namespace '{}' has invalid theme id '{}' — skipping",
                    id.getNamespace(), raw);
                continue;
            }
            declarations.add(new Declaration(id.getNamespace(), themeId));
        }

        if (declarations.isEmpty()) {
            selected = null;
            return;
        }

        if (declarations.size() > 1) {
            // Iteration order on the input map isn't strictly load-order, but
            // the *last* one we accept is what gets used downstream. Make the
            // collision visible.
            StringBuilder sb = new StringBuilder();
            for (Declaration d : declarations) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(d.namespace).append("=").append(d.themeId);
            }
            NeoOrigins.LOGGER.warn(
                "[theming] multiple datapacks declared an active UI theme ({}). "
                + "The last one wins — adjust pack load order if you want a different choice.",
                sb);
        }

        Declaration winner = declarations.get(declarations.size() - 1);
        selected = winner.themeId;
        NeoOrigins.LOGGER.info("[theming] active UI theme set by '{}' datapack: {}",
            winner.namespace, winner.themeId);
    }

    /** The active theme id, or {@code null} if no datapack declared one. */
    public ResourceLocation getSelected() {
        return selected;
    }

    private record Declaration(String namespace, ResourceLocation themeId) {}
}
