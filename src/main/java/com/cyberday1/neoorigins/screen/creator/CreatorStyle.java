package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG2);
        g.renderOutline(x, y, w, h, BORDER);
    }

    /** Thin horizontal rule. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER);
    }

    /** Accent-coloured section title with an underline rule across {@code w}. */
    public static void sectionHeader(GuiGraphics g, Font font, String text,
                                     int x, int y, int w) {
        g.drawString(font, text, x, y, SECTION, false);
        divider(g, x, y + 11, w);
    }

    /** Field label, brighter when the field is required. */
    public static void label(GuiGraphics g, Font font, String text,
                             int x, int y, boolean required) {
        g.drawString(font, required ? text + " *" : text, x, y,
            required ? LABEL_REQ : LABEL, false);
    }

    /** Centered dim helper/empty-state line. */
    public static void emptyState(GuiGraphics g, Font font, String text,
                                  int cx, int y) {
        g.drawCenteredString(font, Component.literal(text), cx, y, TEXT_DIM);
    }

    /**
     * Boxed multi-line hover tooltip near {@code (mx,my)}, clamped on screen.
     * Drawn by tabs after their widgets so it sits on top.
     */
    public static void tooltip(GuiGraphics g, Font font, java.util.List<String> lines,
                               int mx, int my, int screenW, int screenH) {
        if (lines.isEmpty()) return;
        int wMax = 0;
        for (String s : lines) wMax = Math.max(wMax, font.width(s));
        int bw = wMax + 8, bh = lines.size() * 10 + 4;
        int bx = Math.min(mx + 12, screenW - bw - 6);
        int by = Math.min(Math.max(my - 6, 4), screenH - bh - 6);
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xF0060612);
        g.renderOutline(bx - 3, by - 3, bw + 6, bh + 6, ACCENT);
        int ly = by + 2;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), bx + 2, ly,
                i == 0 ? SECTION : TEXT, false);
            ly += 10;
        }
    }
}
