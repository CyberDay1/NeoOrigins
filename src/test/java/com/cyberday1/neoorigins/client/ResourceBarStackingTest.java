package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.client.ResourceBarStacking.Bar;
import com.cyberday1.neoorigins.client.ResourceBarStacking.Box;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cyberday1.neoorigins.client.ResourceBarStacking.NO_SLOT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two rules for the default bar stack. A hidden bar, or one dragged to a saved
 * position, takes no slot, so the bars above it close up. And each slot is as
 * tall as what its bar draws: the old fixed 16 px stride put one bar's value
 * line on top of the next bar's label, which the overlap checks below show.
 */
// hub: neoorigins/resource-bar-first-spend.md
class ResourceBarStackingTest {

    /** The old fixed stride; a bar with no text still uses it. */
    private static final int OLD = 16;

    // Bars with no text, so every stride is the old 16 px.
    private static Bar shown(String key)  { return new Bar(key, true, false, Box.PLAIN, false, false); }
    private static Bar hidden(String key) { return new Bar(key, false, false, Box.PLAIN, false, false); }
    private static Bar placed(String key) { return new Bar(key, true, true, Box.PLAIN, false, false); }

    private static Bar text(String key, Box box) { return new Bar(key, true, false, box, true, true); }

    private static int[] offsets(Bar... bars) {
        return ResourceBarStacking.stackOffsets(List.of(bars));
    }

    // ---- which bars take a slot ----

    @Test
    void middleHiddenBarClosesUp() {
        assertArrayEquals(new int[] { 0, NO_SLOT, OLD }, offsets(shown("a"), hidden("b"), shown("c")));
    }

    @Test
    void savedPositionDoesNotTakeASlot() {
        assertArrayEquals(new int[] { 0, NO_SLOT, OLD }, offsets(shown("a"), placed("b"), shown("c")));
    }

    @Test
    void allHiddenAssignsNothing() {
        assertArrayEquals(new int[] { NO_SLOT, NO_SLOT, NO_SLOT }, offsets(hidden("a"), hidden("b"), hidden("c")));
    }

    @Test
    void firstVisibleBarIsAlwaysAtTheBase() {
        assertArrayEquals(new int[] { NO_SLOT, NO_SLOT, 0 }, offsets(hidden("a"), hidden("b"), shown("c")));
        assertArrayEquals(new int[] { NO_SLOT, 0, OLD }, offsets(placed("a"), shown("b"), shown("c")));
    }

    @Test
    void hiddenAtBothEndsAndSeveralAtOnce() {
        assertArrayEquals(new int[] { NO_SLOT, 0, NO_SLOT, NO_SLOT, OLD, NO_SLOT },
            offsets(hidden("a"), shown("b"), hidden("c"), hidden("d"), shown("e"), hidden("f")));
    }

    @Test
    void savedAndHiddenInterleavedWithStacked() {
        assertArrayEquals(new int[] { 0, NO_SLOT, NO_SLOT, OLD, NO_SLOT, 2 * OLD },
            offsets(shown("a"), placed("b"), hidden("c"), shown("d"),
                    new Bar("e", false, true, Box.PLAIN, true, true), shown("f")));
    }

    // ---- how tall a slot is ----

    @Test
    void barsWithoutTextKeepTheOldStride() {
        for (Box box : List.of(Box.PLAIN, Box.SPRITE, Box.ANIMATED, Box.framed(8))) {
            Bar a = new Bar("a", true, false, box, false, false);
            Bar b = new Bar("b", true, false, box, false, false);
            assertEquals(OLD, ResourceBarStacking.stride(a, b), "box " + box);
        }
    }

    @Test
    void framedBarsWithTextGetTheirMeasuredStride() {
        // label 9 above + (frame 8 + gap 2 + value line 9) below + 1 clear row
        assertArrayEquals(new int[] { 0, 29, 58 },
            offsets(text("a", Box.framed(8)), text("b", Box.framed(8)), text("c", Box.framed(8))));
    }

    @Test
    void eachBoxKindMeasuresItsOwnValueLine() {
        assertEquals(26, ResourceBarStacking.stride(text("a", Box.PLAIN), text("b", Box.PLAIN)));
        assertEquals(26, ResourceBarStacking.stride(text("a", Box.SPRITE), text("b", Box.SPRITE)));
        assertEquals(31, ResourceBarStacking.stride(text("a", Box.ANIMATED), text("b", Box.ANIMATED)));
        // The stride takes the upper bar's bottom and the lower bar's top.
        assertEquals(31, ResourceBarStacking.stride(text("a", Box.PLAIN), text("b", Box.ANIMATED)));
        assertEquals(26, ResourceBarStacking.stride(text("a", Box.ANIMATED), text("b", Box.PLAIN)));
    }

    @Test
    void labelOnlyAndValueOnly() {
        Bar labelOnly = new Bar("a", true, false, Box.framed(8), true, false);
        Bar valueOnly = new Bar("b", true, false, Box.framed(8), false, true);
        // lower label 9 + upper frame 8 + 1
        assertEquals(18, ResourceBarStacking.stride(labelOnly, labelOnly));
        // lower frame top 0 + upper value bottom 19 + 1
        assertEquals(20, ResourceBarStacking.stride(valueOnly, valueOnly));
    }

    @Test
    void textNeverOverlapsAtAnyMix() {
        List<Box> boxes = List.of(Box.PLAIN, Box.SPRITE, Box.ANIMATED, Box.framed(8), Box.framed(12));
        for (Box lo : boxes) {
            for (Box hi : boxes) {
                for (int mask = 0; mask < 16; mask++) {
                    Bar lower = new Bar("a", true, false, lo, (mask & 1) != 0, (mask & 2) != 0);
                    Bar upper = new Bar("b", true, false, hi, (mask & 4) != 0, (mask & 8) != 0);
                    int stride = ResourceBarStacking.stride(lower, upper);
                    // Upper anchor is `stride` rows above the lower one; its lowest
                    // drawn row must clear the lower bar's highest by at least GAP.
                    int clear = stride - upper.bottom() - lower.top();
                    assertTrue(clear >= ResourceBarStacking.GAP, lo + "/" + hi + " mask " + mask);
                    assertTrue(stride >= OLD);
                }
            }
        }
    }

    @Test
    void theOldStrideOverlappedTextBars() {
        // What the bug report showed: at 16 px the value line of the bar above
        // ran into the label of the bar below.
        Bar a = text("a", Box.framed(8));
        Bar b = text("b", Box.framed(8));
        assertTrue(OLD - b.bottom() - a.top() < 0);
    }

    @Test
    void hiddenAndSavedBarsDoNotShapeTheStride() {
        // A tall hidden bar between two short ones leaves the short stride.
        Bar tallHidden = new Bar("b", false, false, Box.framed(40), true, true);
        Bar tallPlaced = new Bar("c", true, true, Box.framed(40), true, true);
        assertArrayEquals(new int[] { 0, NO_SLOT, NO_SLOT, 26 },
            offsets(text("a", Box.PLAIN), tallHidden, tallPlaced, text("d", Box.PLAIN)));
    }
}
