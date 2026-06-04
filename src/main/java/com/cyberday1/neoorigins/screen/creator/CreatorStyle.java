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

    /** {@code snake_case} / {@code camelCase} JSON key → "Title Case" for display. */
    public static String title(String key) {
        if (key == null || key.isEmpty()) return "";
        StringBuilder b = new StringBuilder(key.length() + 4);
        boolean cap = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == ' ') { b.append(' '); cap = true; }
            else if (Character.isUpperCase(c) && i > 0 && !cap) { b.append(' ').append(c); }
            else { b.append(cap ? Character.toUpperCase(c) : c); cap = false; }
        }
        return b.toString();
    }

    /** Field label (humanised), brighter when the field is required. */
    public static void label(GuiGraphics g, Font font, String key,
                             int x, int y, boolean required) {
        String t = title(key);
        g.drawString(font, required ? t + " *" : t, x, y,
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
     *
     * <p>Each incoming line is word-wrapped to a sane max width so a long power
     * description no longer renders a single mega-line that runs off the left
     * edge (negative bx) and overlaps the Save/Apply/Close footer. The whole box
     * is then clamped so it stays fully on-screen: it flips to the left of the
     * cursor when it would overflow the right edge, and is pinned above the
     * footer rather than under it.
     */
    public static void tooltip(GuiGraphics g, Font font, java.util.List<String> lines,
                               int mx, int my, int screenW, int screenH) {
        if (lines.isEmpty()) return;

        // Target wrap width: comfortably narrow, but never wider than the screen.
        int maxW = Math.min(220, Math.max(120, screenW - 24));
        java.util.List<String> wrapped = new java.util.ArrayList<>();
        java.util.List<Boolean> isHeader = new java.util.ArrayList<>();
        for (int li = 0; li < lines.size(); li++) {
            boolean header = (li == 0);
            for (String piece : wrap(font, lines.get(li), maxW)) {
                wrapped.add(piece);
                isHeader.add(header);
                header = false; // only the very first visual row is the title colour
            }
        }
        if (wrapped.isEmpty()) return;

        int wMax = 0;
        for (String s : wrapped) wMax = Math.max(wMax, font.width(s));
        int bw = wMax + 8, bh = wrapped.size() * 10 + 4;

        // Prefer to the right of the cursor; flip left if it would overflow.
        int bx = mx + 12;
        if (bx + bw + 6 > screenW) bx = mx - 12 - bw;
        bx = Math.max(6, Math.min(bx, screenW - bw - 6));
        int by = Math.min(Math.max(my - 6, 4), screenH - bh - 6);
        by = Math.max(4, by);

        // Fully opaque (0xFF) so the EditBox placeholder text and cycle-button
        // labels underneath don't bleed through; the box is drawn after a
        // g.flush() in OriginCreatorScreen, but a non-opaque fill still lets
        // pixels behind show.
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xFF060612);
        g.renderOutline(bx - 3, by - 3, bw + 6, bh + 6, ACCENT);
        int ly = by + 2;
        for (int i = 0; i < wrapped.size(); i++) {
            g.drawString(font, wrapped.get(i), bx + 2, ly,
                isHeader.get(i) ? SECTION : TEXT, false);
            ly += 10;
        }
    }

    /** Greedy word-wrap of {@code text} so no visual line exceeds {@code maxW} px. */
    private static java.util.List<String> wrap(Font font, String text, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() == 0) {
                cur.append(word);
            } else if (font.width(cur + " " + word) <= maxW) {
                cur.append(' ').append(word);
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
            // Hard-break a single word that is itself wider than the box.
            while (font.width(cur.toString()) > maxW && cur.length() > 1) {
                int cut = cur.length();
                while (cut > 1 && font.width(cur.substring(0, cut)) > maxW) cut--;
                out.add(cur.substring(0, cut));
                cur.delete(0, cut);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
