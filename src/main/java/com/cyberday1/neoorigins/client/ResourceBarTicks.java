package com.cyberday1.neoorigins.client;

/**
 * Where the value-scale ticks of a resource bar fall, in whole pixels.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
public final class ResourceBarTicks {

    /** Value units between the taller, brighter ticks. */
    public static final int MAJOR_STEP = 25;
    /** Value units between the short, faint ticks. */
    public static final int MINOR_STEP = 5;

    private ResourceBarTicks() {}

    /**
     * Pixel offsets, from the left edge of a {@code width}-pixel track, of a tick every
     * {@code step} value units between {@code min} and {@code max}. The ends are left
     * out, since they sit on the border, as is any tick that rounds onto it. Each tick goes to the nearest whole pixel, so
     * the rounding spreads out and no two gaps differ by more than one pixel. Empty
     * when the ticks would be closer than 2 px, which is too dense to read.
     */
    public static int[] positions(int min, int max, int step, int width) {
        long range = (long) max - min;
        if (range <= 0 || step <= 0 || width <= 0) return new int[0];
        if ((double) width * step / range < 2.0) return new int[0];
        int count = (int) ((range - 1) / step);
        int[] out = new int[count];
        int n = 0;
        for (int i = 0; i < count; i++) {
            long v = (long) (i + 1) * step;
            // round(v * width / range), half up, in integers so no float error creeps in
            int p = (int) ((2 * v * width + range) / (2 * range));
            // A value just short of max can round onto the right border; leave it out.
            if (p > 0 && p < width) out[n++] = p;
        }
        return n == count ? out : java.util.Arrays.copyOf(out, n);
    }

    /** Tick height for a bar {@code barH} pixels tall: 30% for major ticks, 15% for minor, at least 1. */
    public static int height(int barH, boolean major) {
        return Math.max(1, Math.round(barH * (major ? 0.3f : 0.15f)));
    }
}
