package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One source of truth for the creator's look: palette, spacing metrics, and a
 * handful of draw helpers (panel, divider, section header, field label). Tabs
 * pull constants/helpers from here so every screen lines up and recolours
 * together instead of each tab hand-picking ARGB values.
 */
public final class CreatorStyle {

    private CreatorStyle() {}

    // ── palette ─────────────────────────────────────────────────────────────
    public static final int SCRIM      = 0xCC05050E; // full-screen dim
    public static final int PANEL_BG   = 0xFF101020;
    public static final int PANEL_BG2  = 0xFF0B0B17; // inset/content
    public static final int BORDER     = 0xFF2A2A48;
    public static final int ACCENT     = 0xFF4A90D9;
    public static final int TEXT       = 0xFFE6E6F2;
    public static final int TEXT_DIM   = 0xFF9A9AB8;
    public static final int LABEL      = 0xFFBFC0D6;
    public static final int LABEL_REQ  = 0xFFF1E6A2; // required field
    public static final int HINT       = 0xFFD9A94A; // asset-path / advice
    public static final int SECTION    = 0xFF8FB7E8;
    public static final int OK         = 0xFF57DD79;
    public static final int ERR        = 0xFFE2566B;

    // ── metrics ─────────────────────────────────────────────────────────────
    public static final int PAD       = 8;
    public static final int ROW_H     = 22;
    public static final int FIELD_H   = 16;
    public static final int LABEL_W   = 132;
    public static final int HEADER_H  = 20;

    /** Filled, outlined content panel. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG2);
        g.outline(x, y, w, h, BORDER);
    }

    /** Thin horizontal rule. */
    public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER);
    }

    /** Accent-coloured section title with an underline rule across {@code w}. */
    public static void sectionHeader(GuiGraphicsExtractor g, Font font, String text,
                                     int x, int y, int w) {
        g.text(font, text, x, y, SECTION, false);
        divider(g, x, y + 11, w);
    }

    /** Field label, brighter when the field is required. */
    public static void label(GuiGraphicsExtractor g, Font font, String text,
                             int x, int y, boolean required) {
        g.text(font, required ? text + " *" : text, x, y,
            required ? LABEL_REQ : LABEL, false);
    }

    /** Centered dim helper/empty-state line. */
    public static void emptyState(GuiGraphicsExtractor g, Font font, String text,
                                  int cx, int y) {
        g.centeredText(font, Component.literal(text), cx, y, TEXT_DIM);
    }
}
