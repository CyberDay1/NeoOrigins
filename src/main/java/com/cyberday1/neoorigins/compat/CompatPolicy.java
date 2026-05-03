package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;

/**
 * Explicit fail-policy constants for the compat subsystem.
 *
 * Route A (OriginsPowerTranslator): unknown type → {@code Optional.empty()} — fail-closed, no power loaded.
 * Route B actions (ActionParser):   unknown type → {@link #NOOP_ACTION}     — fail-open, silently skipped.
 * Route B conditions (ConditionParser): unknown → {@link #FALSE_CONDITION}  — fail-closed, ability suppressed.
 *
 * Using named constants instead of inline lambdas makes the policy visible at the call site.
 */
public final class CompatPolicy {

    private CompatPolicy() {}

    /** Route B action fallback: unknown action type is silently skipped. */
    public static final EntityAction NOOP_ACTION = p -> {};

    /** Route B condition fallback: unknown condition type suppresses the ability. */
    public static final EntityCondition FALSE_CONDITION = p -> false;

    /**
     * Thread-local counter tracking how many conditions failed closed during
     * the current parse operation. Route B power parsers call
     * {@link #resetFailClosedCount()} before parsing conditions, then check
     * {@link #failClosedCount()} afterwards to detect unsupported condition types.
     */
    private static final ThreadLocal<Integer> FAIL_CLOSED_COUNT = ThreadLocal.withInitial(() -> 0);

    /** Increment the fail-closed counter. Called by ConditionParser.failClosed(). */
    public static void recordFailClosed() { FAIL_CLOSED_COUNT.set(FAIL_CLOSED_COUNT.get() + 1); }

    /** Reset the counter before parsing a condition tree. */
    public static void resetFailClosedCount() { FAIL_CLOSED_COUNT.set(0); }

    /** Returns how many conditions failed closed since the last reset. */
    public static int failClosedCount() { return FAIL_CLOSED_COUNT.get(); }
}
