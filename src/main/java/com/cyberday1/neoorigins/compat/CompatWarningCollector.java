package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accumulates compat-parser warnings during a reload pass so the loader can
 * emit a single deduplicated summary block instead of one WARN per occurrence.
 *
 * <p>Lifecycle: {@link #beginSession()} is called by
 * {@code OriginsCompatPowerLoader.apply()} at the top of a reload, and
 * {@link #emitSummaryAndEndSession()} at the bottom. While a session is
 * active, parser failure sites call into {@code record*} methods which
 * increment per-type counters; the per-occurrence detail drops to DEBUG.
 * When no session is active (e.g. the parser is invoked from a Route A
 * codec deserialization outside a compat reload), the {@code record*}
 * methods fall back to emitting the original WARN immediately, preserving
 * pre-refactor behavior.
 */
public final class CompatWarningCollector {

    private CompatWarningCollector() {}

    private static final AtomicBoolean SESSION = new AtomicBoolean(false);

    // Per-category counters: typeName -> occurrences
    private static final Map<String, AtomicInteger> UNSUPPORTED_ACTION   = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> UNSUPPORTED_CONDITION = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> ITEM_ACTION_UNSUPPORTED = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> ITEM_CONDITION_UNSUPPORTED = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> MODIFIER_DEFAULTED  = new ConcurrentHashMap<>();

    // Single-counter categories
    private static final AtomicInteger ITEM_ACTION_PARSE_ERRORS    = new AtomicInteger();
    private static final AtomicInteger ITEM_CONDITION_PARSE_ERRORS = new AtomicInteger();
    private static final AtomicInteger MODIFIER_PARSE_ERRORS       = new AtomicInteger();
    private static final AtomicInteger SNBT_MALFORMED              = new AtomicInteger();

    // Compile-time per-power failures: "id (type)" -> reason (kept for the summary list)
    private static final List<String> POWER_COMPILE_FAILURES = new ArrayList<>();

    public static void beginSession() {
        SESSION.set(true);
        UNSUPPORTED_ACTION.clear();
        UNSUPPORTED_CONDITION.clear();
        ITEM_ACTION_UNSUPPORTED.clear();
        ITEM_CONDITION_UNSUPPORTED.clear();
        MODIFIER_DEFAULTED.clear();
        ITEM_ACTION_PARSE_ERRORS.set(0);
        ITEM_CONDITION_PARSE_ERRORS.set(0);
        MODIFIER_PARSE_ERRORS.set(0);
        SNBT_MALFORMED.set(0);
        synchronized (POWER_COMPILE_FAILURES) { POWER_COMPILE_FAILURES.clear(); }
    }

    public static boolean isSessionActive() { return SESSION.get(); }

    // ── recording entry points (one per spammy site) ───────────────────────

    public static void recordUnsupportedAction(String type, String contextId, String detail) {
        if (SESSION.get()) {
            UNSUPPORTED_ACTION.computeIfAbsent(type, k -> new AtomicInteger()).incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] action '{}' in {} defaulted to no-op: {}",
                type, contextId, detail);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] action '{}' in {} defaulted to no-op: {}",
                type, contextId, detail);
        }
    }

    public static void recordUnsupportedCondition(String type, String contextId, String detail) {
        if (SESSION.get()) {
            UNSUPPORTED_CONDITION.computeIfAbsent(type, k -> new AtomicInteger()).incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] condition '{}' in {} failed closed: {}",
                type, contextId, detail);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] condition '{}' in {} failed closed: {}",
                type, contextId, detail);
        }
    }

    public static void recordItemActionUnsupported(String type) {
        if (SESSION.get()) {
            ITEM_ACTION_UNSUPPORTED.computeIfAbsent(type, k -> new AtomicInteger()).incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] item_action unsupported type '{}' — no-op", type);
        } else {
            NeoOrigins.LOGGER.debug("[CompatB] item_action unsupported type '{}' — no-op", type);
        }
    }

    public static void recordItemActionParseError(String type, String message) {
        if (SESSION.get()) {
            ITEM_ACTION_PARSE_ERRORS.incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] item_action parse error ({}): {}", type, message);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] item_action parse error ({}): {}", type, message);
        }
    }

    public static void recordItemConditionUnsupported(String type) {
        if (SESSION.get()) {
            ITEM_CONDITION_UNSUPPORTED.computeIfAbsent(type, k -> new AtomicInteger()).incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] item_condition unsupported type '{}' — defaults to true", type);
        } else {
            NeoOrigins.LOGGER.debug("[CompatB] item_condition unsupported type '{}' — defaults to true", type);
        }
    }

    public static void recordItemConditionParseError(String type, String message) {
        if (SESSION.get()) {
            ITEM_CONDITION_PARSE_ERRORS.incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] item_condition parse error ({}): {}", type, message);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] item_condition parse error ({}): {}", type, message);
        }
    }

    public static void recordSnbtMalformed(String where, String snbt) {
        if (SESSION.get()) {
            SNBT_MALFORMED.incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] {}: malformed SNBT '{}'", where, snbt);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] {}: malformed SNBT '{}'", where, snbt);
        }
    }

    public static void recordModifierDefault(String op, String contextId) {
        if (SESSION.get()) {
            MODIFIER_DEFAULTED.computeIfAbsent(op, k -> new AtomicInteger()).incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] modifier '{}' in {} defaulted to identity", op, contextId);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] modifier '{}' in {} defaulted to identity", op, contextId);
        }
    }

    public static void recordModifierParseError(String contextId, String message) {
        if (SESSION.get()) {
            MODIFIER_PARSE_ERRORS.incrementAndGet();
            NeoOrigins.LOGGER.debug("[CompatB] modifier parse error in {}: {}", contextId, message);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] modifier parse error in {}: {}", contextId, message);
        }
    }

    public static void recordPowerCompileFailure(String id, String type, String reason) {
        if (SESSION.get()) {
            synchronized (POWER_COMPILE_FAILURES) {
                POWER_COMPILE_FAILURES.add(id + " (" + type + "): " + reason);
            }
            NeoOrigins.LOGGER.debug("[CompatB] Failed to load {} ({}): {}", id, type, reason);
        } else {
            NeoOrigins.LOGGER.warn("[CompatB] Failed to load {} ({}): {}", id, type, reason);
        }
    }

    // ── summary emission ───────────────────────────────────────────────────

    /**
     * Emit the deduplicated summary (one WARN-level block if anything was
     * collected, nothing if the pack parsed cleanly) and close the session.
     * Safe to call even if {@link #beginSession()} was never called.
     */
    public static void emitSummaryAndEndSession() {
        if (!SESSION.get()) return;
        try {
            boolean any = !UNSUPPORTED_ACTION.isEmpty()
                || !UNSUPPORTED_CONDITION.isEmpty()
                || !ITEM_ACTION_UNSUPPORTED.isEmpty()
                || !ITEM_CONDITION_UNSUPPORTED.isEmpty()
                || !MODIFIER_DEFAULTED.isEmpty()
                || ITEM_ACTION_PARSE_ERRORS.get() > 0
                || ITEM_CONDITION_PARSE_ERRORS.get() > 0
                || MODIFIER_PARSE_ERRORS.get() > 0
                || SNBT_MALFORMED.get() > 0
                || !POWER_COMPILE_FAILURES.isEmpty();
            if (!any) return;

            StringBuilder sb = new StringBuilder();
            sb.append("[CompatB] Compatibility summary — pack reload:\n");
            appendTypeBreakdown(sb, "  Unsupported action types",      UNSUPPORTED_ACTION);
            appendTypeBreakdown(sb, "  Unsupported condition types",   UNSUPPORTED_CONDITION);
            appendTypeBreakdown(sb, "  Item action fallbacks",         ITEM_ACTION_UNSUPPORTED);
            appendTypeBreakdown(sb, "  Item condition fallbacks",      ITEM_CONDITION_UNSUPPORTED);
            appendTypeBreakdown(sb, "  Modifier defaults",             MODIFIER_DEFAULTED);
            appendSingleCount(sb, "  Item action parse errors",        ITEM_ACTION_PARSE_ERRORS.get());
            appendSingleCount(sb, "  Item condition parse errors",     ITEM_CONDITION_PARSE_ERRORS.get());
            appendSingleCount(sb, "  Modifier parse errors",           MODIFIER_PARSE_ERRORS.get());
            appendSingleCount(sb, "  Malformed SNBT blobs",            SNBT_MALFORMED.get());

            List<String> failures;
            synchronized (POWER_COMPILE_FAILURES) {
                failures = new ArrayList<>(POWER_COMPILE_FAILURES);
            }
            if (!failures.isEmpty()) {
                sb.append("  Powers failed to compile (").append(failures.size()).append("):\n");
                for (String f : failures) sb.append("    - ").append(f).append('\n');
            }

            sb.append("  Set log level to DEBUG on com.cyberday1.neoorigins to see per-occurrence detail.");
            NeoOrigins.LOGGER.warn(sb.toString());
        } finally {
            SESSION.set(false);
        }
    }

    private static void appendTypeBreakdown(StringBuilder sb, String label, Map<String, AtomicInteger> counts) {
        if (counts.isEmpty()) return;
        int distinct = counts.size();
        int total = counts.values().stream().mapToInt(AtomicInteger::get).sum();
        sb.append(label).append(" (").append(distinct)
          .append(distinct == 1 ? " type, " : " types, ")
          .append(total).append(total == 1 ? " occurrence): " : " occurrences): ");
        // Sort by count desc then name asc for stable, scannable output.
        List<Map.Entry<String, AtomicInteger>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, AtomicInteger>>comparingInt(e -> -e.getValue().get())
            .thenComparing(Map.Entry::getKey));
        boolean first = true;
        for (var e : entries) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(" (x").append(e.getValue().get()).append(')');
            first = false;
        }
        sb.append('\n');
    }

    private static void appendSingleCount(StringBuilder sb, String label, int count) {
        if (count <= 0) return;
        sb.append(label).append(": ").append(count).append('\n');
    }
}
