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
    public static final ModConfigSpec.BooleanValue CLASSIC_PICKER_STYLE;
    public static final ModConfigSpec.BooleanValue SHOW_ORIGIN_EDITOR;
    public static final ModConfigSpec.BooleanValue HIDE_HUD_BARS;

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

        CLASSIC_PICKER_STYLE = BUILDER
            .comment("Revert the origin/class selection screens to the original flat",
                     "high-contrast style (dark panels, light text, vanilla font) instead",
                     "of the parchment scroll skin. Enable this if the parchment theme's",
                     "low-contrast brown-on-paper text is hard to read.")
            .define("classic_picker_style", false);

        SHOW_ORIGIN_EDITOR = BUILDER
            .comment("Show the in-game Origin Editor button on the origin info screen for",
                     "ALL players, not just those in Creative mode. The editor is a",
                     "pack-authoring tool that is creative-only by default to keep it out",
                     "of survival players' way. Enable this if you author origins in",
                     "survival or want testers to reach the editor without /gamemode.")
            .define("show_origin_editor", false);

        BUILDER.pop();

        BUILDER.comment("Heads-up-display options local to this client.").push("hud");

        HIDE_HUD_BARS = BUILDER
            .comment("Hide hunger / air HUD bars for origins that don't consume them",
                     "(e.g. Automaton hunger, Merling / Kraken / Automaton air).",
                     "Turn off to keep vanilla bars visible regardless of origin.")
            .define("hide_hud_bars", true);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** True if the selection screens should use the original flat high-contrast skin. */
    public static boolean isClassicPickerStyle() { return CLASSIC_PICKER_STYLE.get(); }

    /** True if the in-game Origin Editor button should be shown regardless of game mode. */
    public static boolean isShowOriginEditor() { return SHOW_ORIGIN_EDITOR.get(); }

    /** True if vanilla hunger/air HUD bars should be hidden for non-consuming origins. */
    public static boolean isHideHudBarsEnabled() { return HIDE_HUD_BARS.get(); }

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
