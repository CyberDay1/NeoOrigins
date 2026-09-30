package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cyberday1.neoorigins.client.ResourceBarStacking.NO_SLOT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * A hidden bar, or one dragged to a saved position, used to keep its stacked
 * slot, so the bars above it did not close up. Slots now go only to bars that
 * draw in the stack. The helper encodes the old counter so the middle-hidden
 * case is shown to disagree with it.
 */
// hub: neoorigins/resource-bar-first-spend.md
class ResourceBarStackingTest {

    private static ResourceBarStacking.Bar shown(String key)  { return new ResourceBarStacking.Bar(key, true, false); }
    private static ResourceBarStacking.Bar hidden(String key) { return new ResourceBarStacking.Bar(key, false, false); }
    private static ResourceBarStacking.Bar placed(String key) { return new ResourceBarStacking.Bar(key, true, true); }

    private static int[] slots(ResourceBarStacking.Bar... bars) {
        return ResourceBarStacking.assignSlots(List.of(bars));
    }

    /** The shipped counter: every bar advanced it, drawn or not, stacked or not. */
    private static int[] oldSlots(ResourceBarStacking.Bar... bars) {
        int[] out = new int[bars.length];
        for (int i = 0; i < bars.length; i++) {
            out[i] = bars[i].drawn() && !bars[i].hasSavedPosition() ? i : NO_SLOT;
        }
        return out;
    }

    @Test
    void middleHiddenBarClosesUp() {
        var bars = new ResourceBarStacking.Bar[] { shown("a"), hidden("b"), shown("c") };
        assertArrayEquals(new int[] { 0, NO_SLOT, 1 }, slots(bars));
        assertArrayEquals(new int[] { 0, NO_SLOT, 2 }, oldSlots(bars), "the old counter left the gap");
    }

    @Test
    void savedPositionDoesNotTakeASlot() {
        assertArrayEquals(new int[] { 0, NO_SLOT, 1 }, slots(shown("a"), placed("b"), shown("c")));
    }

    @Test
    void allHiddenAssignsNothing() {
        assertArrayEquals(new int[] { NO_SLOT, NO_SLOT, NO_SLOT }, slots(hidden("a"), hidden("b"), hidden("c")));
    }

    @Test
    void firstVisibleBarIsAlwaysSlotZero() {
        assertArrayEquals(new int[] { NO_SLOT, NO_SLOT, 0 }, slots(hidden("a"), hidden("b"), shown("c")));
        assertArrayEquals(new int[] { NO_SLOT, 0, 1 }, slots(placed("a"), shown("b"), shown("c")));
    }

    @Test
    void hiddenAtBothEndsAndSeveralAtOnce() {
        assertArrayEquals(new int[] { NO_SLOT, 0, NO_SLOT, NO_SLOT, 1, NO_SLOT },
            slots(hidden("a"), shown("b"), hidden("c"), hidden("d"), shown("e"), hidden("f")));
    }

    @Test
    void savedAndHiddenInterleavedWithStacked() {
        assertArrayEquals(new int[] { 0, NO_SLOT, NO_SLOT, 1, NO_SLOT, 2 },
            slots(shown("a"), placed("b"), hidden("c"), shown("d"),
                  new ResourceBarStacking.Bar("e", false, true), shown("f")));
    }
}
