package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import net.minecraft.resources.Identifier;

/**
 * Resource-loadable UI theme. Drives panel textures, text colours, and
 * font selection for the NeoOrigins screens (selection + info).
 *
 * <p>The default {@link #PARCHMENT} theme ships with the mod; resource packs
 * can override it by placing a JSON at
 * {@code assets/<ns>/ui_themes/<id>.json}. Loaded by {@link UIThemeManager}
 * on every client reload.
 *
 * <p><b>Font note:</b> {@link #font()} returns an {@link Identifier}
 * pointing at a font provider JSON under {@code assets/<ns>/font/}. The
 * default ({@code neoorigins:parchment}) chains the bundled Newsreader TTF
 * to Minecraft's built-in {@code minecraft:default}. To plug in a different
 * serif font drop the TTF into
 * {@code src/main/resources/assets/neoorigins/font/} and replace the
 * provider entry in {@code parchment.json} with a {@code "type": "ttf"}
 * block referencing the file. No code changes are required.
 */
public final class UITheme {

    /** Built-in parchment theme — burnt-paper panel, dark-brown text. */
    public static final UITheme PARCHMENT = new UITheme(
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "textures/gui/themes/parchment/panel.png"),
        0xCC060610,          // overlayColor — full-screen scrim (kept dark per UX spec)
        0xFF2A1810,          // nameColor — origin display name (dark brown / near-black)
        0xFF3A2410,          // descriptionColor — body description
        0xFF6B3B10,          // powerNameColor — warm umber
        0xFF4A2A10,          // powerDescriptionColor — muted brown
        0xFF2A1810,          // headerColor — section headers ("Powers", "Evolution Path")
        0xFF6B4A20,          // borderColor — burnt-edge fallback outline
        0xFF4A2A10,          // mutedColor — secondary text (spawn-location, scroll bar)
        0xFFB87328,          // accentColor — bullet points / impact dots filled
        Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "parchment"),
        // 9-slice insets — left, top, right, bottom (px on a 256x256 source).
        // Burnt edges occupy roughly the outer 12px; tuned by eye.
        12, 12, 12, 12,
        256, 256,
        false, 0
    );

    /**
     * Original flat high-contrast skin (dark navy panels, light text, vanilla
     * font). Used when {@code classic_picker_style} is enabled in the config as
     * a readability fallback for the parchment theme. {@link #flat()} switches
     * the panel/button renderers from textured 9-slice/scroll art to plain
     * fill + outline.
     */
    public static final UITheme CLASSIC = new UITheme(
        null,                // panelBackground unused in flat mode
        0xCC060610,          // overlayColor — full-screen scrim
        0xFFFFFFFF,          // nameColor — selected/origin name (white)
        0xFF9999BB,          // descriptionColor — body / unselected row name
        0xFF7AACDA,          // powerNameColor — soft blue
        0xFF8892A8,          // powerDescriptionColor — light grey (bumped from the
                             //   original 0xFF445566 for legibility on the dark panel)
        0xFFCCCCDD,          // headerColor — section headers
        0xFF2A2A44,          // borderColor — panel/button outline
        0xFF666688,          // mutedColor — secondary hints
        0xFF4A90D9,          // accentColor — accent bars, selected outline, dots
        null,                // font — vanilla default (no decorative font)
        0, 0, 0, 0,
        0, 0,
        true, 0xFF0E0E1C     // flat = true; panelColor — dark navy panel fill
    );

    private static UITheme current = PARCHMENT;

    public static UITheme current() {
        // The classic-style config is an explicit accessibility override and
        // takes precedence over any resource-pack-installed theme.
        if (NeoOriginsConfig.isClassicPickerStyle()) return CLASSIC;
        return current;
    }

    public static void setCurrent(UITheme theme) {
        current = theme != null ? theme : PARCHMENT;
    }

    private final Identifier panelBackground;
    private final int overlayColor;
    private final int nameColor;
    private final int descriptionColor;
    private final int powerNameColor;
    private final int powerDescriptionColor;
    private final int headerColor;
    private final int borderColor;
    private final int mutedColor;
    private final int accentColor;
    private final Identifier font;
    private final int insetLeft, insetTop, insetRight, insetBottom;
    private final int textureWidth, textureHeight;
    private final boolean flat;
    private final int panelColor;

    public UITheme(Identifier panelBackground,
                   int overlayColor,
                   int nameColor,
                   int descriptionColor,
                   int powerNameColor,
                   int powerDescriptionColor,
                   int headerColor,
                   int borderColor,
                   int mutedColor,
                   int accentColor,
                   Identifier font,
                   int insetLeft, int insetTop, int insetRight, int insetBottom,
                   int textureWidth, int textureHeight,
                   boolean flat, int panelColor) {
        this.panelBackground = panelBackground;
        this.overlayColor = overlayColor;
        this.nameColor = nameColor;
        this.descriptionColor = descriptionColor;
        this.powerNameColor = powerNameColor;
        this.powerDescriptionColor = powerDescriptionColor;
        this.headerColor = headerColor;
        this.borderColor = borderColor;
        this.mutedColor = mutedColor;
        this.accentColor = accentColor;
        this.font = font;
        this.insetLeft = insetLeft;
        this.insetTop = insetTop;
        this.insetRight = insetRight;
        this.insetBottom = insetBottom;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.flat = flat;
        this.panelColor = panelColor;
    }

    public Identifier panelBackground() { return panelBackground; }
    public int overlayColor() { return overlayColor; }
    public int nameColor() { return nameColor; }
    public int descriptionColor() { return descriptionColor; }
    public int powerNameColor() { return powerNameColor; }
    public int powerDescriptionColor() { return powerDescriptionColor; }
    public int headerColor() { return headerColor; }
    public int borderColor() { return borderColor; }
    public int mutedColor() { return mutedColor; }
    public int accentColor() { return accentColor; }
    public Identifier font() { return font; }
    public int insetLeft() { return insetLeft; }
    public int insetTop() { return insetTop; }
    public int insetRight() { return insetRight; }
    public int insetBottom() { return insetBottom; }
    public int textureWidth() { return textureWidth; }
    public int textureHeight() { return textureHeight; }
    /** True for the flat high-contrast skin: renderers paint plain fill + outline. */
    public boolean flat() { return flat; }
    /** Panel background fill colour used by the flat skin (ignored when textured). */
    public int panelColor() { return panelColor; }
}
