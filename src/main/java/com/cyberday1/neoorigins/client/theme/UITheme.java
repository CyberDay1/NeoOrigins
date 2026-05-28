package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceLocation;

/**
 * Resource-loadable UI theme. Drives panel textures, text colours, and
 * font selection for the NeoOrigins screens (selection + info).
 *
 * <p>The default {@link #PARCHMENT} theme ships with the mod; resource packs
 * can override it by placing a JSON at
 * {@code assets/<ns>/ui_themes/<id>.json}. Loaded by {@link UIThemeManager}
 * on every client reload.
 *
 * <p><b>Font note:</b> {@link #font()} returns a {@link ResourceLocation}
 * pointing at a font provider JSON under {@code assets/<ns>/font/}. The
 * default ({@code neoorigins:parchment}) currently chains to Minecraft's
 * built-in {@code minecraft:default}. To plug in a real serif font (e.g.
 * Cinzel, EB Garamond — OFL/SIL licensed) drop the TTF into
 * {@code src/main/resources/assets/neoorigins/font/} and replace the
 * provider entry in {@code parchment.json} with a {@code "type": "ttf"}
 * block referencing the file. No code changes are required.
 */
public final class UITheme {

    /** Built-in parchment theme — burnt-paper panel, dark-brown text. */
    public static final UITheme PARCHMENT = new UITheme(
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "textures/gui/themes/parchment/panel.png"),
        0xCC060610,          // overlayColor — full-screen scrim (kept dark per UX spec)
        0xFF2A1810,          // nameColor — origin display name (dark brown / near-black)
        0xFF3A2410,          // descriptionColor — body description
        0xFF6B3B10,          // powerNameColor — warm umber
        0xFF4A2A10,          // powerDescriptionColor — muted brown
        0xFF2A1810,          // headerColor — section headers ("Powers", "Evolution Path")
        0xFF6B4A20,          // borderColor — burnt-edge fallback outline
        0xFF4A2A10,          // mutedColor — secondary text (spawn-location, scroll bar)
        0xFFB87328,          // accentColor — bullet points / impact dots filled
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "parchment"),
        // 9-slice insets — left, top, right, bottom (px on a 256x256 source).
        // Burnt edges occupy roughly the outer 12px; tuned by eye.
        12, 12, 12, 12,
        256, 256
    );

    private static UITheme current = PARCHMENT;

    public static UITheme current() {
        return current;
    }

    public static void setCurrent(UITheme theme) {
        current = theme != null ? theme : PARCHMENT;
    }

    private final ResourceLocation panelBackground;
    private final int overlayColor;
    private final int nameColor;
    private final int descriptionColor;
    private final int powerNameColor;
    private final int powerDescriptionColor;
    private final int headerColor;
    private final int borderColor;
    private final int mutedColor;
    private final int accentColor;
    private final ResourceLocation font;
    private final int insetLeft, insetTop, insetRight, insetBottom;
    private final int textureWidth, textureHeight;

    public UITheme(ResourceLocation panelBackground,
                   int overlayColor,
                   int nameColor,
                   int descriptionColor,
                   int powerNameColor,
                   int powerDescriptionColor,
                   int headerColor,
                   int borderColor,
                   int mutedColor,
                   int accentColor,
                   ResourceLocation font,
                   int insetLeft, int insetTop, int insetRight, int insetBottom,
                   int textureWidth, int textureHeight) {
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
    }

    public ResourceLocation panelBackground() { return panelBackground; }
    public int overlayColor() { return overlayColor; }
    public int nameColor() { return nameColor; }
    public int descriptionColor() { return descriptionColor; }
    public int powerNameColor() { return powerNameColor; }
    public int powerDescriptionColor() { return powerDescriptionColor; }
    public int headerColor() { return headerColor; }
    public int borderColor() { return borderColor; }
    public int mutedColor() { return mutedColor; }
    public int accentColor() { return accentColor; }
    public ResourceLocation font() { return font; }
    public int insetLeft() { return insetLeft; }
    public int insetTop() { return insetTop; }
    public int insetRight() { return insetRight; }
    public int insetBottom() { return insetBottom; }
    public int textureWidth() { return textureWidth; }
    public int textureHeight() { return textureHeight; }
}
