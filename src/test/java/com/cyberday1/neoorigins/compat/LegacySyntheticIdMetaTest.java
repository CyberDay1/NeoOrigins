package com.cyberday1.neoorigins.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: the 2.2.11 legacy synthetic-id shim resolved slash-form
 * ("parent/subkey") references for ResourceState reads/writes, but NOT for
 * {@link CompatAttachments#getResourceMeta(String)} / getVariable lookups.
 *
 * <p>Real-world failure (Discord tester "Trans Jump" double-jump): a
 * neoorigins:multiple's action_on_event(land) refills a [0,1] counter via
 * change_resource against the pre-2.2.8 slash id. The bounds lookup in
 * BuiltinActions.clampedAddBounded got null meta for the slash key, fell back
 * to MIN_VALUE/MAX_VALUE, and the un-clamped +1-per-landing pushed the counter
 * past max — so the jump's {@code resource == 1} gate went permanently false
 * and the power "just stopped working".
 */
class LegacySyntheticIdMetaTest {

    private static final String LEGACY    = "neo:global/trans_jump/counter";
    private static final String CANONICAL = "neo:global/trans_jump_counter";

    @AfterEach
    void cleanup() {
        CompatAttachments.clearLegacySyntheticIds();
        CompatAttachments.clearResourceMeta();
        CompatAttachments.clearVariables();
    }

    @Test
    void resourceMetaResolvesLegacySlashId() {
        // Expansion registers the alias (OriginsCompatPowerLoader.expandMultiple)…
        CompatAttachments.registerLegacySyntheticId(LEGACY, CANONICAL);
        // …and the resource power registers its meta under the canonical id
        // (ResourcePower.onGranted).
        CompatAttachments.registerResourceMeta(CANONICAL,
            new CompatAttachments.ResourceMeta(0, 1, "Counter", 0xFF55AAFF));

        assertNotNull(CompatAttachments.getResourceMeta(LEGACY),
            "meta lookup by legacy slash id must resolve to the canonical entry");
        assertNotNull(CompatAttachments.getResourceMeta(CANONICAL));
    }

    @Test
    void landRefillStaysClampedThroughLegacyId() {
        CompatAttachments.registerLegacySyntheticId(LEGACY, CANONICAL);
        CompatAttachments.registerResourceMeta(CANONICAL,
            new CompatAttachments.ResourceMeta(0, 1, "Counter", 0xFF55AAFF));

        var state = new CompatAttachments.ResourceState();
        state.set(CANONICAL, 1); // seeded at start_value by ResourcePower.onGranted

        // Faithful replay of BuiltinActions.clampedAddBounded for the
        // action_on_event(land) change_resource(+1) against the LEGACY id:
        var meta = CompatAttachments.getResourceMeta(LEGACY);
        assertNotNull(meta, "pre-fix this was null → unbounded add");
        state.clampedAdd(LEGACY, +1, meta.min(), meta.max());

        // Counter must stay at max=1, so the jump's `resource == 1` gate holds.
        assertEquals(1, state.get(LEGACY, 0));
        assertEquals(1, state.get(CANONICAL, 0));
        assertTrue(state.get(LEGACY, 0) == 1, "double-jump condition resource == 1");

        // Jump spends 1 → 0; another landing refills to exactly 1 again.
        state.clampedAdd(LEGACY, -1, meta.min(), meta.max());
        assertEquals(0, state.get(CANONICAL, 0));
        state.clampedAdd(LEGACY, +1, meta.min(), meta.max());
        assertEquals(1, state.get(CANONICAL, 0));
    }

    @Test
    void unknownKeysAreNeverRewritten() {
        // A genuine slash-path power id that never went through multiple
        // expansion must not be remapped or gain meta.
        assertNull(CompatAttachments.getResourceMeta("ns:folder/standalone_power"));
        assertEquals("ns:folder/standalone_power",
            CompatAttachments.resolveLegacySyntheticId("ns:folder/standalone_power"));
    }

    @Test
    void variableLookupsResolveLegacyIds() {
        CompatAttachments.registerLegacySyntheticId("neo:pack/counters/mana", "neo:pack/counters_mana");
        CompatAttachments.registerVariable("neo:pack/counters_mana",
            new CompatAttachments.VariableDecl(5, 0, 10));

        assertNotNull(CompatAttachments.getVariable("neo:pack/counters/mana"));
        assertTrue(CompatAttachments.isDeclaredVariable("neo:pack/counters/mana"));
        assertEquals(5, CompatAttachments.variableStart("neo:pack/counters/mana"));
    }
}
