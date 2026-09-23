package com.cyberday1.neoorigins.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a resource bar should be drawn, and the first-spend memory that
 * decision needs (#105).
 *
 * <p>Hiding a full bar is the Apoli convention, but 32 of the 33 shipped bars
 * that use it also start at max, so a new player saw nothing at all. A bar the
 * player has never spent therefore keeps rendering; once spent, hide-when-full
 * resumes.
 *
 * <p>Deliberately free of Minecraft types so the rule is unit-testable.
 */
// hub: neoorigins/resource-bar-first-spend.md
public final class ResourceBarVisibility {

    /**
     * Resource keys seen below full at least once this session. Session-scoped
     * on purpose — {@link ClientResourceState#clear()} resets it on each join,
     * so a relog re-advertises the bar rather than hiding it forever.
     */
    private static final Set<String> SPENT = ConcurrentHashMap.newKeySet();

    private ResourceBarVisibility() {}

    /**
     * The draw rule. {@code alwaysShow} is the {@code hud_render.always_render}
     * opt-in and wins outright; otherwise a bar below full always draws, and a
     * full bar draws only while the resource is still unspent.
     */
    public static boolean shouldDraw(boolean alwaysShow, float fraction, boolean spent) {
        return alwaysShow || fraction < 1.0f || !spent;
    }

    /** {@link #shouldDraw(boolean, float, boolean)} against the live spend memory. */
    public static boolean shouldDraw(String key, boolean alwaysShow, float fraction) {
        return shouldDraw(alwaysShow, fraction, hasBeenSpent(key));
    }

    /**
     * Records a synced value. Every path that lowers a resource syncs the new
     * value, so "seen below full" catches every spender without any power
     * implementation having to report one.
     */
    public static void observe(String key, float fraction) {
        if (fraction < 1.0f) SPENT.add(key);
    }

    public static boolean hasBeenSpent(String key) {
        return SPENT.contains(key);
    }

    /** Session reset. Keys are not pruned per-resource — a stale key costs a string. */
    public static void reset() {
        SPENT.clear();
    }
}
