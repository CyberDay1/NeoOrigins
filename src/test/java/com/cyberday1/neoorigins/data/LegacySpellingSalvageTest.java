package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two diagnostics added for power {@code type} fields that resolve to
 * nothing: the {@code neoorigins:} → {@code origins:} legacy salvage, and the
 * hint appended to the "Unknown power type" warning.
 *
 * <p>Both exist because the failure they address is <b>silent</b>. An unresolved
 * type drops the whole power — the origin still loads, just weaker, so the only
 * evidence is one warning line the author has no reason to be reading. Nothing
 * else in the build can see it: the JSON is well-formed, the schema validates a
 * closed enum that these ids are legitimately outside of, and every test stays
 * green. That makes it exactly the kind of behaviour that has to be pinned by a
 * parse-level test or it will regress unnoticed.
 *
 * <p>The negative case is the load-bearing one. The salvage is guarded so it can
 * only ever fire where the alternative was dropping the power outright; a name
 * carried by BOTH vocabularies (e.g. {@code invisibility}) must keep resolving to
 * its native type exactly as before. Lose that guard and the salvage stops being
 * a rescue and starts quietly rerouting powers that load fine today.
 */
class LegacySpellingSalvageTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LegacyPowerTypeAliases.bootstrap();
    }

    private static JsonObject json(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    /** Run one power body through the real pre-parse pipeline, as the loader does. */
    private static PowerDataManager.Resolved resolve(String powerId, String body) {
        return PowerDataManager.resolvePowerType(Identifier.parse(powerId), json(body));
    }

    // ── The registry guard the salvage is built on ──────────────────────────

    /**
     * {@link PowerTypes#isBuiltinPath} has to answer honestly here, or every
     * assertion below is vacuous. It replaced a {@code PowerTypes.get} call for
     * precisely that reason: {@code get} reads a registry that stays null until
     * {@code NewRegistryEvent} fires, so outside a running game its answer depends
     * on whatever else happened to initialise first — measured, this class alone
     * saw a null registry and the full suite did not. A guard that reads "no native
     * type" or "native type" by luck of ordering is not a guard.
     */
    @Test
    void builtinPathLookupWorksOutsideARunningGame() {
        assertTrue(PowerTypes.isBuiltinPath("invisibility"),
            "invisibility is registered in PowerTypes; if this is false the guard is inert");
        assertFalse(PowerTypes.isBuiltinPath("action_on_item_use"),
            "action_on_item_use is legacy-only and must have no native type");
    }

    // ── Salvage: fires where the power would otherwise be dropped ───────────

    /** A Route A legacy type with no native counterpart is rewritten and survives. */
    @Test
    void routeAOnlyTypeSpelledNeoOriginsIsSalvaged() {
        JsonObject body = json("""
            {"type": "neoorigins:action_on_item_use",
             "entity_action": {"type": "origins:heal", "amount": 1}}
            """);
        assertEquals("origins:action_on_item_use",
            OriginsFormatDetector.salvageLegacyPowerSpelling(body));
        assertEquals("origins:action_on_item_use", body.get("type").getAsString(),
            "the salvage rewrites the type in place, so both loaders see it");
    }

    /**
     * Route B's half. {@code OriginsCompatPowerLoader} re-reads the resources
     * through its own {@code prepare}, so {@code PowerDataManager}'s call cannot
     * fix these up for it — the salvage is wired into both loaders, and a Route B
     * type must be rewritten the same way.
     */
    @Test
    void routeBTypeSpelledNeoOriginsIsSalvaged() {
        JsonObject body = json("""
            {"type": "neoorigins:action_over_time", "interval": 20}
            """);
        assertEquals("origins:action_over_time",
            OriginsFormatDetector.salvageLegacyPowerSpelling(body));
    }

    /** End to end: the rewrite reaches Route A and yields a real native type. */
    @Test
    void salvagedTypeResolvesThroughTheFullPipeline() {
        PowerDataManager.Resolved resolved = resolve("testpack:on_use", """
            {"type": "neoorigins:action_on_item_use",
             "entity_action": {"type": "origins:heal", "amount": 1}}
            """);
        assertNotNull(resolved, "the power was dropped — the salvage is not wired into resolvePowerType");
        assertEquals("neoorigins:action_on_event", resolved.typeId().toString());
        assertEquals("item_use", resolved.json().get("event").getAsString());
    }

    // ── The guards: salvage must not poach anything that already loads ──────

    /**
     * ⭐ The critical negative. {@code invisibility} exists in BOTH vocabularies —
     * {@code origins:invisibility} has a Route A dispatch case AND
     * {@code neoorigins:invisibility} is a registered power. It must keep
     * resolving natively, untouched.
     */
    @Test
    void sharedNameKeepsResolvingNatively() {
        JsonObject body = json("""
            {"type": "neoorigins:invisibility"}
            """);
        assertEquals("neoorigins:invisibility",
            OriginsFormatDetector.salvageLegacyPowerSpelling(body),
            "a name with a native power type must never be rerouted to compat");

        PowerDataManager.Resolved resolved = resolve("testpack:vanish", """
            {"type": "neoorigins:invisibility"}
            """);
        assertNotNull(resolved);
        assertEquals("neoorigins:invisibility", resolved.typeId().toString());
    }

    /** A name in neither vocabulary is left alone, to be reported as unknown. */
    @Test
    void nonDispatchableNameIsLeftAlone() {
        JsonObject body = json("""
            {"type": "neoorigins:not_a_real_power_type"}
            """);
        assertEquals("neoorigins:not_a_real_power_type",
            OriginsFormatDetector.salvageLegacyPowerSpelling(body));
    }

    /** Types outside our namespace are not this method's business. */
    @Test
    void foreignNamespacesAreUntouched() {
        assertEquals("origins:action_on_item_use", OriginsFormatDetector
            .salvageLegacyPowerSpelling(json("{\"type\": \"origins:action_on_item_use\"}")));
        assertEquals("somemod:custom_power", OriginsFormatDetector
            .salvageLegacyPowerSpelling(json("{\"type\": \"somemod:custom_power\"}")));
    }

    // ── The hint on the drop path ───────────────────────────────────────────

    /**
     * The case from the field: a pack wrote the Iron's Spells cast ACTION into a
     * power's {@code type} field. All three vocabularies share a namespace and a
     * naming style, so the id reads like a power type; "Unknown power type" alone
     * would not tell the author which of the three they had reached for.
     */
    @Test
    void actionIdInTheTypeFieldIsNamedAsAnAction() {
        String hint = PowerDataManager.unknownTypeHint(
            Identifier.parse("neoorigins:cast_iron_spell"));
        assertTrue(hint.contains("is an action, not a power type"), hint);
        assertTrue(hint.contains("entity_action"), "the hint must name the field to move it to: " + hint);
    }

    /** Matched on the path, so the hint survives a wrong namespace too. */
    @Test
    void actionHintIgnoresTheAuthoredNamespace() {
        assertTrue(PowerDataManager.unknownTypeHint(
                Identifier.parse("origins:cast_iron_spell"))
            .contains("is an action, not a power type"));
    }

    /** Nothing to say is said as nothing — no empty em-dash clause in the log. */
    @Test
    void unrecognizableTypeGetsNoHint() {
        assertEquals("", PowerDataManager.unknownTypeHint(
            Identifier.parse("somemod:entirely_unknown_thing")));
    }
}
