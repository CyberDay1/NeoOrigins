package com.cyberday1.neoorigins.client;

/**
 * Per-column colour of a two-colour ({@code tint} to {@code tint_end}) static bar fill.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
public final class ResourceBarGradient {

    private ResourceBarGradient() {}

    /**
     * Opaque RGB at column {@code col} of a {@code width}-pixel track, shading linearly
     * from {@code start} at column 0 to {@code end} at column {@code width - 1}. The
     * gradient spans the whole track, so a column keeps its colour as the bar fills.
     * Alpha in either input is ignored.
     */
    public static int at(int start, int end, int col, int width) {
        if (width <= 1) return 0xFF000000 | (start & 0xFFFFFF);
        int c = Math.max(0, Math.min(width - 1, col));
        int span = width - 1;
        int r = lerp((start >> 16) & 0xFF, (end >> 16) & 0xFF, c, span);
        int g = lerp((start >> 8) & 0xFF, (end >> 8) & 0xFF, c, span);
        int b = lerp(start & 0xFF, end & 0xFF, c, span);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, int c, int span) {
        return a + Math.round((b - a) * (float) c / span);
    }
}
