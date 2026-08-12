package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code origins:biome} accepts a nested sub-condition in its own small grammar,
 * of which only {@code temperature} was ever implemented. Everything else — and
 * {@code in_tag} is by far the most common of them — hit the fail-closed arm, so
 * Fairy Origin's chromatic aura, which gates on {@code minecraft:is_forest},
 * never once fired in a forest.
 *
 * <p>Fail-closed is the right default here and stays: a biome-gated power that
 * fires everywhere is a louder bug than one that never fires. What changed is
 * how much falls into it.
 *
 * <p>The arms that read the world need a live server, so the ones asserted by
 * evaluation here are the ones that do not ({@code constant}, the combinators
 * over it, and {@code inverted}). For the world-reading arms the assertion is
 * that they no longer resolve to the fail-closed constant — a constant-false
 * lambda returns false for a null player, whereas a real body dereferences it
 * and throws.
 */
class BiomeSubConditionTest {

    private static JsonObject sub(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    /** A biome condition wrapping the given sub-condition, as packs author it. */
    private static JsonObject biomeWith(JsonObject subCondition) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "origins:biome");
        o.add("condition", subCondition);
        return o;
    }

    // ---- still fails closed ---------------------------------------------

    @Test
    void unknownSubTypeStillFailsClosed() {
        assertFalse(ConditionParser.parseBiomeSubCondition(sub("origins:not_a_real_thing")).test(null));
    }

    @Test
    void nullSubConditionFailsClosed() {
        assertFalse(ConditionParser.parseBiomeSubCondition(null).test(null));
    }

    @Test
    void inTagWithoutATagFailsClosed() {
        assertFalse(ConditionParser.parseBiomeSubCondition(sub("origins:in_tag")).test(null));
    }

    @Test
    void precipitationWithoutAValueFailsClosed() {
        assertFalse(ConditionParser.parseBiomeSubCondition(sub("origins:precipitation")).test(null));
    }

    @Test
    void precipitationWithAnUnknownValueFailsClosed() {
        JsonObject o = sub("origins:precipitation");
        o.addProperty("precipitation", "hail");
        assertFalse(ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    // ---- constant + combinators (evaluable without a world) --------------

    @Test
    void constantHonoursItsValue() {
        JsonObject t = sub("origins:constant");
        t.addProperty("value", true);
        assertTrue(ConditionParser.parseBiomeSubCondition(t).test(null));

        JsonObject f = sub("origins:constant");
        f.addProperty("value", false);
        assertFalse(ConditionParser.parseBiomeSubCondition(f).test(null));
    }

    @Test
    void invertedNegatesARecognisedSubCondition() {
        JsonObject o = sub("origins:constant");
        o.addProperty("value", true);
        o.addProperty("inverted", true);
        assertFalse(ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    /**
     * Inversion must NOT flip the fail-closed arm into a fail-open one: an
     * unsupported sub-type wrapped in {@code inverted: true} would otherwise
     * become always-true, which is the exact failure mode fail-closed exists to
     * prevent.
     */
    @Test
    void invertedDoesNotTurnAnUnknownSubTypeIntoAlwaysTrue() {
        JsonObject o = sub("origins:not_a_real_thing");
        o.addProperty("inverted", true);
        assertFalse(ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    @Test
    void andRequiresEveryBranch() {
        assertTrue(ConditionParser.parseBiomeSubCondition(combinator("origins:and", true, true)).test(null));
        assertFalse(ConditionParser.parseBiomeSubCondition(combinator("origins:and", true, false)).test(null));
        assertTrue(ConditionParser.parseBiomeSubCondition(combinator("origins:all_of", true, true)).test(null));
    }

    @Test
    void orRequiresOneBranch() {
        assertTrue(ConditionParser.parseBiomeSubCondition(combinator("origins:or", true, false)).test(null));
        assertFalse(ConditionParser.parseBiomeSubCondition(combinator("origins:or", false, false)).test(null));
        assertTrue(ConditionParser.parseBiomeSubCondition(combinator("origins:any_of", false, true)).test(null));
    }

    @Test
    void notNegatesItsChild() {
        JsonObject child = sub("origins:constant");
        child.addProperty("value", true);
        JsonObject o = sub("origins:not");
        o.add("condition", child);
        assertFalse(ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    private static JsonObject combinator(String type, boolean... values) {
        JsonObject o = sub(type);
        JsonArray arr = new JsonArray();
        for (boolean v : values) {
            JsonObject c = sub("origins:constant");
            c.addProperty("value", v);
            arr.add(c);
        }
        o.add("conditions", arr);
        return o;
    }

    // ---- world-reading arms reach a real body ----------------------------

    /**
     * Fairy Origin's shape, verbatim. Before the fix this returned the
     * fail-closed constant, so it simply answered false; now it reaches a body
     * that reads {@code player.level()} and therefore throws on a null player.
     * The throw is the evidence — a constant-false lambda cannot throw.
     */
    @Test
    void inTagReachesARealBody() {
        JsonObject o = sub("origins:in_tag");
        o.addProperty("tag", "minecraft:is_forest");
        assertThrows(NullPointerException.class,
            () -> ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    @Test
    void precipitationReachesARealBody() {
        JsonObject o = sub("origins:precipitation");
        o.addProperty("precipitation", "snow");
        assertThrows(NullPointerException.class,
            () -> ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    @Test
    void highHumidityReachesARealBody() {
        assertThrows(NullPointerException.class,
            () -> ConditionParser.parseBiomeSubCondition(sub("origins:high_humidity")).test(null));
    }

    @Test
    void temperatureStillReachesARealBody() {
        JsonObject o = sub("origins:temperature");
        o.addProperty("comparison", ">");
        o.addProperty("compare_to", 0.5);
        assertThrows(NullPointerException.class,
            () -> ConditionParser.parseBiomeSubCondition(o).test(null));
    }

    /** The wrapper routes into the sub-grammar, not into the entity-condition one. */
    @Test
    void biomeWrapperDelegatesToTheSubGrammar() {
        JsonObject inner = sub("origins:constant");
        inner.addProperty("value", false);
        assertFalse(ConditionParser.parse(biomeWith(inner), "test:biome_sub").test(null));

        JsonObject unknown = sub("origins:not_a_real_thing");
        assertFalse(ConditionParser.parse(biomeWith(unknown), "test:biome_sub_unknown").test(null));
    }

    // ---- precipitation value mapping -------------------------------------

    @Test
    void precipitationValuesMapToVanillasEnum() {
        assertSame(Biome.Precipitation.NONE, parsePrecip("none"));
        assertSame(Biome.Precipitation.RAIN, parsePrecip("rain"));
        assertSame(Biome.Precipitation.SNOW, parsePrecip("snow"));
        assertSame(Biome.Precipitation.SNOW, parsePrecip("SNOW"));
        assertNull(parsePrecip("sleet"));
    }

    private static Biome.Precipitation parsePrecip(String value) {
        JsonObject o = sub("origins:precipitation");
        o.addProperty("precipitation", value);
        return ConditionParser.parsePrecipitation(o);
    }
}
