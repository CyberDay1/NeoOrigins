package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.theme.UITheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Paints the origin-selector buttons as a parchment scroll.
 *
 * <p>Two source textures, both {@value #TEX_W}×{@value #TEX_H}: a rolled-up
 * scroll for the resting state and an unrolled scroll for the active/hover
 * state. They are drawn with a horizontal 3-slice — the rolled end-caps are
 * blitted at a fixed width (scaled only to the button height) while the
 * parchment middle is stretched to fill — so the rolls never distort no matter
 * how wide the button is.
 *
 * <p>On hover the open texture is re-blitted with a warm translucent tint to
 * give a soft glow.
 *
 * <p>26.1 port: uses {@link GuiGraphicsExtractor} with the tinted scaled-blit
 * overload ({@code blit(pipeline, tex, x, y, u, v, w, h, srcW, srcH, texW, texH, color)}).
 * The master alpha and warm glow are expressed as ARGB tint colors instead of
 * the old {@code GuiGraphics.setColor} + {@code RenderSystem.enableBlend}, which
 * no longer exist — {@link RenderPipelines#GUI_TEXTURED} blends on its own.
 */
public final class ScrollButtonRenderer {

    private ScrollButtonRenderer() {}

    public static final Identifier CLOSED = Identifier.fromNamespaceAndPath(
        "neoorigins", "textures/gui/themes/parchment/scroll_close_long.png");
    public static final Identifier OPEN = Identifier.fromNamespaceAndPath(
        "neoorigins", "textures/gui/themes/parchment/scroll_open_long.png");

    /** Compact scroll art (less vertical bulk) for the origin/class list rows. */
    public static final Identifier CLOSED_SHORT = Identifier.fromNamespaceAndPath(
        "neoorigins", "textures/gui/themes/parchment/scroll_close_short.png");
    public static final Identifier OPEN_SHORT = Identifier.fromNamespaceAndPath(
        "neoorigins", "textures/gui/themes/parchment/scroll_open_short.png");

    /** Native texture dimensions of the *_long scroll art. */
    private static final int TEX_W = 120;
    private static final int TEX_H = 25;
    /** Fixed source width of each rolled end-cap (the non-stretched region). */
    private static final int CAP = 12;

    /**
     * @param active true to use the open (unrolled) scroll — selected or hovered
     * @param glow   true to overlay the warm hover glow (typically == hovered)
     * @param alpha  master alpha (use &lt;1 to dim a disabled button)
     */
    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            boolean active, boolean glow, float alpha) {
        draw(g, x, y, w, h, active, glow, alpha, false);
    }

    /**
     * @param shortStyle true to use the compact {@code *_short} scroll art
     *                   (origin/class list rows); false for the full {@code *_long} art
     */
    public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h,
                            boolean active, boolean glow, float alpha, boolean shortStyle) {
        if (UITheme.current().flat()) {
            flatFill(g, x, y, w, h, active, glow);
            return;
        }
        Identifier open   = shortStyle ? OPEN_SHORT   : OPEN;
        Identifier closed = shortStyle ? CLOSED_SHORT : CLOSED;
        Identifier tex = active ? open : closed;
        int a = Math.round(alpha * 255f) & 0xFF;
        int mainColor = (a << 24) | 0xFFFFFF;
        threeSlice(g, tex, x, y, w, h, mainColor);
        if (glow) {
            // Warm translucent tint (RGB 255,237,168 @ 0.30 alpha) — equivalent to
            // the 1.21.1 setColor(1.0f, 0.93f, 0.66f, 0.30f * alpha) glow overlay.
            int ga = Math.round(0.30f * alpha * 255f) & 0xFF;
            int glowColor = (ga << 24) | 0xFFEDA8;
            threeSlice(g, open, x, y, w, h, glowColor);
        }
    }

    /**
     * Original flat high-contrast button skin used when the active theme is flat.
     * {@code active && !glow} ⇒ selected; {@code glow} ⇒ hovered; else resting.
     * (OriginButton passes active=selected||hovered, glow=hovered; ParchmentButton
     * passes active=glow=hovered, so action buttons only ever show rest/hover.)
     */
    private static void flatFill(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                 boolean active, boolean glow) {
        boolean selected = active && !glow;
        int bg     = selected ? 0xFF1E3A6E : (glow ? 0xFF1E1E32 : 0xFF14141F);
        int border = selected ? 0xFF4A90D9 : 0xFF2A2A44;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
    }

    /** Left cap + right cap at fixed width (height-scaled), parchment middle stretched. */
    private static void threeSlice(GuiGraphicsExtractor g, Identifier tex,
                                   int x, int y, int w, int h, int color) {
        int cap = Math.min(CAP, w / 2);
        int srcMidW = Math.max(1, TEX_W - 2 * CAP);
        int dstMidW = Math.max(0, w - 2 * cap);
        // Left cap.
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 0.0f,
            cap, h, CAP, TEX_H, TEX_W, TEX_H, color);
        // Right cap.
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - cap, y, (float) (TEX_W - CAP), 0.0f,
            cap, h, CAP, TEX_H, TEX_W, TEX_H, color);
        // Parchment middle.
        if (dstMidW > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + cap, y, (float) CAP, 0.0f,
                dstMidW, h, srcMidW, TEX_H, TEX_W, TEX_H, color);
        }
    }
}
