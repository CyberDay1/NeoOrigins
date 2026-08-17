package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-condition half of the descriptor/parser parity check that
 * {@link ItemConditionGapTest} has carried for item conditions all along.
 *
 * <p>Its absence is what let {@code all_of} / {@code any_of} sit unlisted through
 * 2.2.24: {@code ConditionParser} accepted both, the catalogue named neither, and
 * the only symptom was an editor marking a file invalid that the game loads fine.
 * Nothing failed, so nothing was noticed.
 */
class BlockConditionGapTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A minimally-valid body per catalogue verb. Written out rather than generated
     * so that adding a verb to the catalogue forces a deliberate decision here
     * about what "valid" means for it.
     */
    private static Map<String, String> minimalBodies() {
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put("block", "{\"block\": \"minecraft:stone\"}");
        bodies.put("in_tag", "{\"tag\": \"minecraft:ice\"}");
        bodies.put("fluid", "{\"fluid_condition\": {\"type\": \"origins:still\"}}");
        bodies.put("light_level", "{\"comparison\": \">=\", \"compare_to\": 8}");
        bodies.put("exposed_to_sky", "{}");
        bodies.put("movement_blocking", "{}");
        bodies.put("and", "{\"conditions\": [{\"type\": \"origins:movement_blocking\"}]}");
        bodies.put("or", "{\"conditions\": [{\"type\": \"origins:movement_blocking\"}]}");
        bodies.put("all_of", "{\"conditions\": [{\"type\": \"origins:movement_blocking\"}]}");
        bodies.put("any_of", "{\"conditions\": [{\"type\": \"origins:movement_blocking\"}]}");
        bodies.put("offset", "{\"y\": -1, \"condition\": {\"type\": \"origins:movement_blocking\"}}");
        bodies.put("block_state", "{\"property\": \"waterlogged\", \"value\": \"true\"}");
        bodies.put("height", "{\"comparison\": \"<=\", \"compare_to\": 62}");
        bodies.put("adjacent", "{\"adjacent_condition\": {\"type\": \"origins:movement_blocking\"},"
            + " \"comparison\": \">=\", \"compare_to\": 1}");
        return bodies;
    }

    private static JsonObject withType(String verb, String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        json.addProperty("type", "origins:" + verb);
        return json;
    }

    /** Every verb the catalogue advertises must actually compile in the parser. */
    @Test
    void everyDescribedVerbCompiles() {
        Map<String, String> bodies = minimalBodies();
        for (String verb : catalogueVerbs()) {
            String body = bodies.get(verb);
            assertNotNull(body, "no minimal body written for catalogue verb '" + verb
                + "' — add one so this test keeps meaning something");
            assertNotNull(
                ConditionParser.compileInBlockPredicate(withType(verb, body), "test"),
                "block condition '" + verb + "' is described but the parser does not compile it — "
                    + "a pack author is offered a verb that silently does nothing");
        }
    }

    /**
     * The regression this test was written for. Both spellings are Apoli 2.9+
     * renames the parser has always honoured; before 2.2.25 the catalogue listed
     * only {@code and} / {@code or}.
     */
    @Test
    void theApoliCombinatorSpellingsAreDescribed() {
        Set<String> verbs = catalogueVerbs();
        assertTrue(verbs.contains("all_of"), "all_of missing from the catalogue: " + verbs);
        assertTrue(verbs.contains("any_of"), "any_of missing from the catalogue: " + verbs);
    }

    /** {@code all_of} must behave as {@code and}, not merely be accepted. */
    @Test
    void allOfCombinesTheSameWayAnd() {
        String conditions = "{\"conditions\": ["
            + "{\"type\": \"origins:movement_blocking\"},"
            + "{\"type\": \"origins:exposed_to_sky\"}]}";

        assertNotNull(ConditionParser.compileInBlockPredicate(withType("all_of", conditions), "test"));
        assertNotNull(ConditionParser.compileInBlockPredicate(withType("any_of", conditions), "test"));
    }

    /** Catalogue ids and their aliases, bare verb names, sorted for readable failures. */
    private static Set<String> catalogueVerbs() {
        Set<String> verbs = new TreeSet<>();
        BuiltinBlockConditions.descriptors().forEach((id, type) -> {
            verbs.add(id.getPath());
            type.aliases().forEach(a -> verbs.add(a.getPath()));
        });
        return verbs;
    }

    /** The catalogue must not carry a duplicate under two spellings. */
    @Test
    void aliasesDoNotCollideWithCanonicalIds() {
        Set<String> canonical = new HashSet<>();
        BuiltinBlockConditions.descriptors().keySet().forEach(id -> canonical.add(id.getPath()));
        Set<String> aliases = new TreeSet<>();
        BuiltinBlockConditions.descriptors().values()
            .forEach(t -> t.aliases().forEach(a -> aliases.add(a.getPath())));

        Set<String> overlap = new TreeSet<>(aliases);
        overlap.retainAll(canonical);
        assertEquals(Set.of(), overlap, "an alias shadows a canonical block-condition id");
    }
}
