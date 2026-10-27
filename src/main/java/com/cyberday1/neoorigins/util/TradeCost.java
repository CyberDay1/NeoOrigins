package com.cyberday1.neoorigins.util;

import net.minecraft.util.Mth;

/** Turns a modified trade cost back into whole items, rounding in the player's favour. */
public final class TradeCost {

    private TradeCost() {}

    /**
     * Rounds {@code scaled} to a usable cost: half an item is taken off before
     * rounding, so a discount is never rounded away and a cost of 2 or more always
     * drops by at least 1 ({@code 2 -> 1}, {@code 5 -> 4}, {@code 20 -> 18}).
     *
     * <p>Plain {@link Math#round} alone would round 4.5 back up to 5, undoing the
     * whole discount on the small emerald trades it matters most for. An unmodified
     * cost is unchanged, since {@code round(n - 0.5)} is {@code n} for a whole n.
     */
    public static int rounded(float scaled, int max) {
        return Mth.clamp(Math.round(scaled - 0.5f), 1, max);
    }
}
