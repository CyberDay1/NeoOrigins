package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.screen.OriginSelectionPresenter;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side TOML config for NeoOrigins. Stored at
 * {@code config/neoorigins/client.toml} in the game directory.
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
    public static final ModConfigSpec.EnumValue<OriginSelectionPresenter.SortMode> DEFAULT_SORT;
    public static final ModConfigSpec.BooleanValue HIDE_HUD_BARS;
    public static final ModConfigSpec.BooleanValue SHOW_COOLDOWN_COUNTDOWN;
    public static final ModConfigSpec.IntValue COOLDOWN_COUNTDOWN_OPACITY;
    public static final ModConfigSpec.EnumValue<HudAbilityDisplay> HUD_ABILITY_DISPLAY;
    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_ABILITY_ICONS;

    /** What the cooldown/ability HUD cluster shows besides live cooldowns. */
    public enum HudAbilityDisplay {
        /** Default: live cooldowns + icon-bearing toggle powers (bright = on, dimmed = off). */
        COOLDOWNS_AND_TOGGLES,
        /** Every keybind ability with an icon keeps a persistent slot, idle or not. */
        ALL_ACTIVE_ABILITIES
    }

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

        DEFAULT_SORT = BUILDER
            .comment("Initial sort order for the origin selection / info screens, used",
                     "until you cycle the on-screen sort button (your cycled choice still",
                     "wins for the rest of the session). Options:",
                     "  CLASS      - grouped by mod/namespace, alphabetical within (default)",
                     "  NAME_ASC   - flat alphabetical",
                     "  NAME_DESC  - flat reverse-alphabetical",
                     "  IMPACT_ASC - by origin impact/influence (none -> low -> medium -> high)",
                     "Set IMPACT_ASC to open the picker sorted by influence.")
            .defineEnum("default_sort", OriginSelectionPresenter.SortMode.CLASS);

        BUILDER.pop();

        BUILDER.comment("Heads-up-display options local to this client.").push("hud");

        HIDE_HUD_BARS = BUILDER
            .comment("Hide hunger / air HUD bars for origins that don't consume them",
                     "(e.g. Automaton hunger, Merling / Kraken / Automaton air).",
                     "Turn off to keep vanilla bars visible regardless of origin.")
            .define("hide_hud_bars", true);

        SHOW_COOLDOWN_COUNTDOWN = BUILDER
            .comment("Master switch for the numeric seconds drawn on cooldown icons.",
                     "Packs opt individual powers in via \"cooldown_countdown\": true;",
                     "set this to false to suppress ALL countdown numbers on this client.")
            .define("show_cooldown_countdown", true);

        COOLDOWN_COUNTDOWN_OPACITY = BUILDER
            .comment("Opacity (in percent) of the seconds countdown drawn on cooldown",
                     "icons, so the number reads without hiding the icon underneath.",
                     "100 = fully opaque, 0 = invisible. Values below 5 are treated",
                     "as 5 when rendering (the font renderer drops text below that).")
            .defineInRange("cooldown_countdown_opacity", 70, 0, 100);

        HUD_ABILITY_DISPLAY = BUILDER
            .comment("What the ability HUD cluster shows besides live cooldowns.",
                     "ALL_ACTIVE_ABILITIES (default): every keybind ability with an icon",
                     "keeps a persistent slot — full-bright while idle, cooldown sweep",
                     "while recharging, bright/dim for toggles.",
                     "COOLDOWNS_AND_TOGGLES: cooldown slots only while recharging, plus",
                     "icon-bearing toggleable powers (bright = on, dimmed = off).")
            .defineEnum("hud_ability_display", HudAbilityDisplay.ALL_ACTIVE_ABILITIES);

        ALWAYS_SHOW_ABILITY_ICONS = BUILDER
            .comment("Force every icon-bearing ability to stay on the HUD cluster even",
                     "while off cooldown (as if each power declared",
                     "\"always_show_icon\": true). Default false: idle cooldown icons",
                     "disappear unless the power itself opts in.")
            .define("always_show_ability_icons", false);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** True if the selection screens should use the original flat high-contrast skin. */
    public static boolean isClassicPickerStyle() { return CLASSIC_PICKER_STYLE.get(); }

    /** True if the in-game Origin Editor button should be shown regardless of game mode. */
    public static boolean isShowOriginEditor() { return SHOW_ORIGIN_EDITOR.get(); }

    /** Initial sort order for the origin selection / info screens. */
    public static OriginSelectionPresenter.SortMode defaultSortMode() { return DEFAULT_SORT.get(); }

    /** True if vanilla hunger/air HUD bars should be hidden for non-consuming origins. */
    public static boolean isHideHudBarsEnabled() { return HIDE_HUD_BARS.get(); }

    /** True if cooldown icons may draw their numeric seconds countdown (client master switch). */
    public static boolean isShowCooldownCountdown() { return SHOW_COOLDOWN_COUNTDOWN.get(); }

    /** Opacity (0–100 percent) of the countdown number on cooldown icons. */
    public static int cooldownCountdownOpacity() { return COOLDOWN_COUNTDOWN_OPACITY.get(); }

    /** What the ability HUD cluster shows besides live cooldowns. */
    public static HudAbilityDisplay hudAbilityDisplay() { return HUD_ABILITY_DISPLAY.get(); }

    /** True if every icon-bearing ability should keep its HUD slot while idle. */
    public static boolean isAlwaysShowAbilityIcons() { return ALWAYS_SHOW_ABILITY_ICONS.get(); }

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
