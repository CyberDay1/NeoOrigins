package com.cyberday1.neoorigins.client;

/**
 * Scroll offset for a framed bar style's fill strip.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
public final class ResourceBarScroll {

    private ResourceBarScroll() {}

    /**
     * How far the strip has drifted left, in GUI pixels, within {@code [0, loopW)}.
     * Driven by game ticks plus the partial tick, so it moves between ticks and stops
     * while the game is paused. Snapped down to whole screen pixels, so the strip
     * steps one screen pixel at a time and is never resampled between texels.
     *
     * @param ticks    game time in ticks, including the partial tick
     * @param speed    GUI pixels per second; negative drifts right
     * @param loopW    texels per loop
     * @param guiScale screen pixels per GUI pixel
     */
    public static double offset(double ticks, float speed, int loopW, double guiScale) {
        if (loopW <= 0) return 0.0;
        double off = (ticks / 20.0 * speed) % loopW;
        if (off < 0) off += loopW;
        if (guiScale > 0) off = Math.floor(off * guiScale) / guiScale;
        return off >= loopW ? 0.0 : off;
    }
}
