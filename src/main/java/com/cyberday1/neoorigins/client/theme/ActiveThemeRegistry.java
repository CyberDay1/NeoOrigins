package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side resolver that picks which {@link UITheme} the origin screens
 * should render. Precedence (highest wins):
 *
 * <ol>
 *   <li><b>Player override</b> — {@code ui.theme_override} in
 *       {@code config/neoorigins-client.toml}. Lets solo players force a
 *       specific theme regardless of what the server declares.</li>
 *   <li><b>Server / datapack-declared theme</b> — last value received via
 *       {@code SyncActiveThemePayload} (sourced from
 *       {@code data/<ns>/neoorigins/active_theme.json}).</li>
 *   <li><b>Built-in default</b> — {@link UIThemeManager#DEFAULT_ID}
 *       ({@code neoorigins:parchment}).</li>
 * </ol>
 *
 * <p>Any layer setting an unknown id is logged at {@code warn} and skipped so
 * resolution falls through to the next layer (typo-friendly).
 *
 * <p>The selected theme is pushed to {@link UITheme#setCurrent(UITheme)} on
 * each resolve. Screens read {@link UITheme#current()} per render call, so
 * changes are visible the next frame.
 */
public final class ActiveThemeRegistry {

    private ActiveThemeRegistry() {}

    private static volatile ResourceLocation serverDeclared; // from SyncActiveThemePayload
    private static volatile ResourceLocation clientOverride; // from neoorigins-client.toml

    /** Server told us which theme its datapack(s) selected (may be null = no declaration). */
    public static void setServerDeclared(ResourceLocation id) {
        serverDeclared = id;
        reapply();
    }

    /** Local override read from the client config TOML. Empty string → cleared. */
    public static void setClientOverride(String raw) {
        if (raw == null || raw.isBlank()) {
            clientOverride = null;
        } else {
            ResourceLocation parsed = ResourceLocation.tryParse(raw.trim());
            if (parsed == null) {
                NeoOrigins.LOGGER.warn(
                    "[theming] ui.theme_override is not a valid resource id: '{}' — ignoring", raw);
                clientOverride = null;
            } else {
                clientOverride = parsed;
            }
        }
        reapply();
    }

    /** Clear the server-declared theme on disconnect so the next single-player world starts clean. */
    public static void clearServerDeclared() {
        serverDeclared = null;
        reapply();
    }

    /** Re-resolve {@link UITheme#current()} from the current sources. Called on any source change
     *  or after the theme map is reloaded. */
    public static void reapply() {
        ResourceLocation chosen = resolve();
        UITheme theme = UIThemeManager.get(chosen);
        if (theme == null) {
            NeoOrigins.LOGGER.warn(
                "[theming] active theme '{}' is not loaded — falling back to {}",
                chosen, UIThemeManager.DEFAULT_ID);
            theme = UITheme.PARCHMENT;
        }
        UITheme.setCurrent(theme);
    }

    /** The resource id that should be active right now (after precedence). Never null. */
    public static ResourceLocation resolve() {
        if (clientOverride != null && UIThemeManager.get(clientOverride) != null) {
            return clientOverride;
        }
        if (clientOverride != null) {
            // Set but unknown — already warned at parse time; just fall through.
        }
        if (serverDeclared != null && UIThemeManager.get(serverDeclared) != null) {
            return serverDeclared;
        }
        return UIThemeManager.DEFAULT_ID;
    }
}
