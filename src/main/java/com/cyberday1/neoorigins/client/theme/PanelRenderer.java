package com.cyberday1.neoorigins.client.theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Manual 9-slice blit for the themed panel texture. The source PNG is split
 * by the theme's insets into 4 corners, 4 edges, and 1 centre. Corners are
 * drawn at native size; edges and centre are stretched to fill the panel.
 *
 * <p>Stretching the centre tile (vs. tiling) is intentional — the parchment
 * source has organic noise that looks fine smeared, and stretch is one blit
 * vs. a tiled grid.
 */
public final class PanelRenderer {

    private PanelRenderer() {}

    public static void drawPanel(GuiGraphicsExtractor g, UITheme theme, int x, int y, int w, int h) {
        if (theme.flat()) {
            // Original flat high-contrast skin: solid panel fill + 1px outline.
            g.fill(x, y, x + w, y + h, theme.panelColor());
            g.outline(x, y, w, h, theme.borderColor());
            return;
        }
        Identifier tex = theme.panelBackground();
        int tw = theme.textureWidth();
        int th = theme.textureHeight();
        int l = theme.insetLeft();
        int t = theme.insetTop();
        int r = theme.insetRight();
        int b = theme.insetBottom();

        // Source coordinates of the centre block on the texture.
        int srcCx = l;
        int srcCy = t;
        int srcCw = Math.max(1, tw - l - r);
        int srcCh = Math.max(1, th - t - b);

        // Destination centre block size in the panel.
        int dstCw = Math.max(0, w - l - r);
        int dstCh = Math.max(0, h - t - b);

        // Corners — native size.
        blit(g, tex, x,             y,             l, t,             0,            0,            l, t, tw, th);                  // TL
        blit(g, tex, x + w - r,     y,             r, t,             tw - r,       0,            r, t, tw, th);                  // TR
        blit(g, tex, x,             y + h - b,     l, b,             0,            th - b,       l, b, tw, th);                  // BL
        blit(g, tex, x + w - r,     y + h - b,     r, b,             tw - r,       th - b,       r, b, tw, th);                  // BR

        // Edges — stretched along one axis.
        if (dstCw > 0) {
            blit(g, tex, x + l,         y,             dstCw, t,         srcCx,        0,            srcCw, t, tw, th);          // top
            blit(g, tex, x + l,         y + h - b,     dstCw, b,         srcCx,        th - b,       srcCw, b, tw, th);          // bottom
        }
        if (dstCh > 0) {
            blit(g, tex, x,             y + t,         l, dstCh,         0,            srcCy,        l, srcCh, tw, th);          // left
            blit(g, tex, x + w - r,     y + t,         r, dstCh,         tw - r,       srcCy,        r, srcCh, tw, th);          // right
        }
        // Centre.
        if (dstCw > 0 && dstCh > 0) {
            blit(g, tex, x + l, y + t, dstCw, dstCh, srcCx, srcCy, srcCw, srcCh, tw, th);
        }
    }

    /**
     * Stretched blit. The 26.1 {@link GuiGraphicsExtractor#blit} overload takes
     * dest size ({@code dw,dh}) AND an independent source sub-rect size
     * ({@code sw,sh}); when they differ the source rectangle is stretched into
     * the destination — exactly what a 9-slice needs.
     */
    private static void blit(GuiGraphicsExtractor g, Identifier tex,
                             int dx, int dy, int dw, int dh,
                             int sx, int sy, int sw, int sh,
                             int tw, int th) {
        if (dw <= 0 || dh <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, tex, dx, dy, (float) sx, (float) sy, dw, dh, sw, sh, tw, th);
    }
}
