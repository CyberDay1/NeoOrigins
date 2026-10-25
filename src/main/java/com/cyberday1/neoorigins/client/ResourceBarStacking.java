package com.cyberday1.neoorigins.client;

import java.util.List;

/**
 * Lays out the default resource bar stack. The draw decision is made before
 * any slot is handed out, and only a bar that draws without a saved position
 * takes one, so no reason for a bar to be missing can leave a gap.
 *
 * <p>Each slot is as tall as what its bar draws: the box, the label line
 * above it and the value line below it, so one bar's text never runs into the
 * next bar's. A bar with no text keeps the old fixed 16 px stride.
 *
 * <p>A bar drawn at a scale (the origin editor's per-bar slider) is scaled
 * about its anchor, so its rows above and below the anchor scale with it and
 * its slot grows or shrinks to match.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
// hub: neoorigins/resource-bar-first-spend.md
public final class ResourceBarStacking {

    /** Slot value for a bar that is hidden or placed by a saved position. */
    public static final int NO_SLOT = -1;

    /** The fixed stride every bar used before text was measured; still the floor. */
    public static final int MIN_STRIDE = 16;
    /** Font line height: the label sits this far above the box, and the value line is this tall. */
    public static final int LINE = 9;
    /** Space between the bottom of the box and the value line. */
    public static final int READOUT_GAP = 2;
    /** Clear rows between one bar's value line and the label of the bar above it. */
    public static final int GAP = 1;

    /**
     * What a bar's box covers around its anchor y: {@code above} rows over it
     * (a border, or the Apoli fill overhang), {@code below} rows from it down,
     * and {@code textBase}, the box height the value line is placed under.
     */
    public record Box(int above, int below, int textBase) {
        /** Native colour fill: a 5 px box inside a 1 px border. */
        public static final Box PLAIN = new Box(1, 6, 5);
        /** Apoli sprite: 5 px frame, the fill and icon rise 2 px over it. */
        public static final Box SPRITE = new Box(2, 6, 5);
        /** Unframed FX preset: a 10 px box inside a 1 px border. */
        public static final Box ANIMATED = new Box(1, 11, 10);

        /** Framed preset: the frame texture at its own height, nothing outside it. */
        public static Box framed(int frameH) { return new Box(0, frameH, frameH); }

        /** Offset of the value line from the anchor. */
        public int readoutY() { return textBase + READOUT_GAP; }
    }

    public record Bar(String key, boolean drawn, boolean hasSavedPosition,
                      Box box, boolean label, boolean readout, float scale) {

        /** An unscaled bar. */
        public Bar(String key, boolean drawn, boolean hasSavedPosition,
                   Box box, boolean label, boolean readout) {
            this(key, drawn, hasSavedPosition, box, label, readout, 1.0f);
        }

        /** Rows the bar covers above its anchor, at 100%. */
        public int top() { return label ? Math.max(box.above(), LINE) : box.above(); }

        /** Rows the bar covers from its anchor down, at 100%. */
        public int bottom() { return readout ? Math.max(box.below(), box.readoutY() + LINE) : box.below(); }

        /** Screen rows above the anchor at the bar's scale, rounded up. */
        public int scaledTop() { return scaled(top(), scale); }

        /** Screen rows from the anchor down at the bar's scale, rounded up. */
        public int scaledBottom() { return scaled(bottom(), scale); }
    }

    private ResourceBarStacking() {}

    /**
     * Pixels above the stack base for each bar, in input order; slot 0 is at 0.
     * A bar that is hidden or placed by a saved position gets {@link #NO_SLOT}.
     */
    public static int[] stackOffsets(List<Bar> bars) {
        int[] offsets = new int[bars.size()];
        Bar below = null;
        int y = 0;
        for (int i = 0; i < offsets.length; i++) {
            Bar b = bars.get(i);
            if (!stacked(b)) {
                offsets[i] = NO_SLOT;
                continue;
            }
            if (below != null) y += stride(below, b);
            offsets[i] = y;
            below = b;
        }
        return offsets;
    }

    /**
     * Distance between two neighbouring anchors: the lower bar's top plus the
     * upper bar's bottom, each at its own scale. The 16 px floor scales by the
     * mean of the two.
     */
    public static int stride(Bar lower, Bar upper) {
        int floor = Math.round(MIN_STRIDE * (lower.scale() + upper.scale()) / 2.0f);
        return Math.max(floor, lower.scaledTop() + upper.scaledBottom() + GAP);
    }

    // The epsilon keeps float noise (1.1f * 10 = 11.0000002) from adding a row.
    private static int scaled(int rows, float scale) {
        return (int) Math.ceil(rows * (double) scale - 1e-4);
    }

    private static boolean stacked(Bar b) {
        return b.drawn() && !b.hasSavedPosition();
    }
}
