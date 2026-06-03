package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side TOML config for NeoOrigins. Stored at
 * {@code config/neoorigins-client.toml} in the game directory.
 *
 * <p>Currently holds the UI theme override knob: a string id that, when set,
 * forces a specific {@link com.cyberday1.neoorigins.client.theme.UITheme}
 * regardless of what the connected server's datapacks declared. Empty string =
 * defer to the server / datapack-declared theme (the normal case).
 */
public final class NeoOriginsClientConfig {

    private NeoOriginsClientConfig() {}

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> UI_THEME_OVERRIDE;

    static {
        BUILDER.comment(
            "User-interface theming. Lets you pin a specific theme on this",
            "client regardless of what the server's datapacks declared."
        ).push("ui");

        UI_THEME_OVERRIDE = BUILDER
            .comment(
                "Forced UI theme id (e.g. \"neoorigins:parchment\" or",
                "\"examplepack:dark_woods\"). When non-empty AND the theme is",
                "actually loaded, this overrides the datapack-declared active",
                "theme. Leave empty to follow whatever the server selects.")
            .define("theme_override", "");

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Pushes the current TOML value into {@link com.cyberday1.neoorigins.client.theme.ActiveThemeRegistry}. */
    public static void onConfigLoadOrReload(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        try {
            com.cyberday1.neoorigins.client.theme.ActiveThemeRegistry.setClientOverride(
                UI_THEME_OVERRIDE.get());
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[theming] failed to apply client theme override", e);
        }
    }
}
