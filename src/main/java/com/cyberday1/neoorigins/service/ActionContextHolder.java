package com.cyberday1.neoorigins.service;

/**
 * ThreadLocal holder for the event context currently being dispatched.
 *
 * <p>Phase 6.5 of the 2.0 consolidation: context-aware action verbs
 * (e.g. {@code neoorigins:damage_attacker}, {@code neoorigins:cancel_event})
 * read the active context from here. {@link EventPowerIndex#dispatch} sets
 * the current context around its handler loop and clears it afterwards, so
 * actions see the appropriate context for the event they were triggered by
 * and never leak context across unrelated dispatches.
 *
 * <p>Single-threaded usage on the server thread; the ThreadLocal only exists
 * to isolate nested dispatches (e.g. an action triggering another event).
 */
public final class ActionContextHolder {

    private ActionContextHolder() {}

    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();

    /** Install a context for the current thread. Returns the previous value so callers can restore it (nesting-safe). */
    public static Object set(Object ctx) {
        Object prev = CURRENT.get();
        CURRENT.set(ctx);
        return prev;
    }

    /** Restore a previously-returned context (or null to clear). */
    public static void restore(Object prev) {
        if (prev == null) CURRENT.remove();
        else CURRENT.set(prev);
    }

    /** The context currently being dispatched, or {@code null} outside a dispatch. */
    public static Object get() {
        return CURRENT.get();
    }
}
