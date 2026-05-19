package com.cyberday1.neoorigins.screen.creator.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Scroll-offset bookkeeping + scrollbar chrome for a vertically-overflowing
 * content region (the Powers tab's per-power form). Owns only the math and the
 * scrollbar draw; the tab repositions its registered widgets against
 * {@link #contentTop()} and toggles row visibility via {@link #rowVisible}.
 *
 * <p>Mirrors the in-repo scroll idiom (clamped offset + scissor + thumb) used
 * by {@code OriginEditorScreen}/{@code OriginInfoScreen}; positions only change
 * on scroll, so the tab re-lays out on scroll events rather than per frame.
 */
public final class ScrollPanel {

    private static final int STEP = 14;

    private int viewX, viewY, viewW, viewH;
    private int contentH;
    private int scrollY;

    /** Set the viewport rectangle (the tab's content rect). */
    public void setViewport(int x, int y, int w, int h) {
        viewX = x; viewY = y; viewW = w; viewH = h;
        clamp();
    }

    /** Total laid-out content height; clamps any existing offset. */
    public void setContentHeight(int h) { contentH = h; clamp(); }

    /** Y the first content row starts at, accounting for the scroll offset. */
    public int contentTop() { return viewY - scrollY; }

    public int viewTop()    { return viewY; }
    public int viewBottom() { return viewY + viewH; }

    public int maxScroll()  { return Math.max(0, contentH - viewH); }

    /** Apply a wheel notch; returns true if the offset actually changed. */
    public boolean onScroll(double sy) {
        int prev = scrollY;
        scrollY = Mth.clamp(scrollY + (sy > 0 ? -STEP : STEP), 0, maxScroll());
        return scrollY != prev;
    }

    /** True when a row at [{@code rowTop}, {@code rowTop+rowH}] fits fully in view. */
    public boolean rowVisible(int rowTop, int rowH) {
        return rowTop >= viewY && rowTop + rowH <= viewY + viewH;
    }

    public void beginClip(GuiGraphicsExtractor g) {
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
    }

    public void endClip(GuiGraphicsExtractor g) { g.disableScissor(); }

    /** Draw the right-edge track + thumb when content overflows. */
    public void renderScrollbar(GuiGraphicsExtractor g) {
        int max = maxScroll();
        if (max <= 0) return;
        int barX = viewX + viewW - 3;
        g.fill(barX, viewY, barX + 3, viewY + viewH, 0xFF1A1A28);
        int thumbH = Math.max(16, (int) ((long) viewH * viewH / contentH));
        int thumbY = viewY + (int) ((long) scrollY * (viewH - thumbH) / max);
        g.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFF4A90D9);
    }

    private void clamp() { scrollY = Mth.clamp(scrollY, 0, maxScroll()); }
}
