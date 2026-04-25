package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-shot deprecation warning for DSL verbs that use the legacy
 * {@code origins:} / {@code apace:} namespace instead of the canonical
 * 2.0 {@code neoorigins:} form.
 *
 * <p>Emitted at {@code WARN} level on first parse of each distinct
 * legacy verb per server boot. Grep logs for {@code [2.0-legacy]} to get
 * a migration punch list. Mirrors the existing power-type alias warning
 * machinery in {@code LegacyPowerTypeAliases}.
 */
public final class LegacyVerbWarning {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LegacyVerbWarning() {}

    /**
     * Warn once per distinct legacy verb type. Called from ConditionParser
     * and ActionParser before rewriting the type to the canonical form.
     */
    public static void warn(String legacyType, String canonicalType) {
        if (WARNED.add(legacyType)) {
            NeoOrigins.LOGGER.warn(
                "[2.0-legacy] DSL verb '{}' is deprecated — use '{}'",
                legacyType, canonicalType);
        }
    }

    /** Test hook — resets the warned set so unit tests can re-assert warnings. */
    public static void resetForTest() {
        WARNED.clear();
    }
}
