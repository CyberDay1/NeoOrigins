package com.cyberday1.neoorigins.client;

import java.util.List;

/**
 * Assigns stacked-layout slots to resource bars. The draw decision is made
 * before any slot is handed out, and only a bar that draws without a saved
 * position takes one, so no reason for a bar to be missing can leave a gap.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
// hub: neoorigins/resource-bar-first-spend.md
public final class ResourceBarStacking {

    /** Slot value for a bar that is hidden or placed by a saved position. */
    public static final int NO_SLOT = -1;

    public record Bar(String key, boolean drawn, boolean hasSavedPosition) {}

    private ResourceBarStacking() {}

    /** One slot per bar, in input order; slot 0 sits closest to the hotbar. */
    public static int[] assignSlots(List<Bar> bars) {
        int[] slots = new int[bars.size()];
        int next = 0;
        for (int i = 0; i < slots.length; i++) {
            Bar b = bars.get(i);
            slots[i] = b.drawn() && !b.hasSavedPosition() ? next++ : NO_SLOT;
        }
        return slots;
    }
}
