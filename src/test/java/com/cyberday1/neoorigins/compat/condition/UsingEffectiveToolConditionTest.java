package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Dispatch and fail-safe coverage for {@code neoorigins:using_effective_tool}.
 *
 * <p>The condition mirrors Apoli 2.12.0's {@code apoli:using_effective_tool},
 * verified from that jar's bytecode rather than its docs: it is registered with
 * {@code ConditionConfiguration.simple}, so it takes <b>no config fields at
 * all</b>, and its test is "the player is mid-block-break AND holds the right
 * tool for that block's drops".
 *
 * <p>Every assertion here goes through {@link ConditionParser#parse} rather than
 * the predicate directly. That is deliberate: a verb can be fully implemented and
 * still be unreachable if the parser never resolves its id, which is the exact
 * shape of bug this file exists to catch. An unresolved type does not throw — it
 * fails closed to {@link CompatPolicy#FALSE_CONDITION}, which behaves like a
 * perfectly ordinary always-false condition, so asserting merely that the result
 * is non-null would pass on a completely undispatched verb.
 *
 * <p>⚠ Scope: this covers <b>dispatch</b>, not evaluation. The predicate reads
 * the live break-in-progress off {@code ServerPlayerGameMode}, which a mocked
 * {@code ServerPlayer} does not have — {@code gameMode} is a public final field,
 * so no mock can furnish one. Whether the condition turns true at the right
 * moment is an in-world check, not a unit one, and is not claimed here.
 */
class UsingEffectiveToolConditionTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static EntityCondition parse(String type) {
        return ConditionParser.parse(obj("{\"type\": \"" + type + "\"}"), "test:using_effective_tool");
    }

    /** The verb resolves, rather than falling through to the unknown-type sentinel. */
    @Test
    void theVerbDispatches() {
        EntityCondition parsed = parse("neoorigins:using_effective_tool");
        assertNotNull(parsed);
        assertNotSame(CompatPolicy.FALSE_CONDITION, parsed,
            "condition fell through to fail-closed — the verb is not wired into the parser");
    }

    /**
     * Every namespace a legacy pack might spell it with. {@code apoli:} is the one
     * upstream actually uses, so it is the spelling that matters most here.
     */
    @Test
    void allLegacyNamespacesDispatch() {
        for (String ns : new String[] {"apoli", "origins", "apace", "apugli"}) {
            EntityCondition parsed = parse(ns + ":using_effective_tool");
            assertNotSame(CompatPolicy.FALSE_CONDITION, parsed,
                ns + ":using_effective_tool must canonicalise and dispatch");
        }
    }

    /**
     * Takes no config, so a pack that hangs a stray field on it must still parse
     * rather than fail closed and silently disable the power around it.
     */
    @Test
    void strayFieldsDoNotBreakIt() {
        EntityCondition parsed = ConditionParser.parse(
            obj("{\"type\": \"apoli:using_effective_tool\", \"tool\": \"minecraft:diamond_pickaxe\"}"),
            "test:stray");
        assertNotSame(CompatPolicy.FALSE_CONDITION, parsed);
    }

    /** `inverted` is the Apoli-wide flag; it must apply to a field-less verb too. */
    @Test
    void invertedFlagDispatches() {
        EntityCondition inverted = ConditionParser.parse(
            obj("{\"type\": \"apoli:using_effective_tool\", \"inverted\": true}"), "test:inv");
        assertNotNull(inverted);
        assertNotSame(CompatPolicy.FALSE_CONDITION, inverted);
        assertNotSame(parse("apoli:using_effective_tool"), inverted,
            "the inverted form must be a distinct wrapper, not the bare condition");
    }
}
