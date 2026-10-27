package com.cyberday1.neoorigins.api.origin;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code origins:origin} and {@code apace:origin} are always merged into
 * {@code neoorigins:origin}, so a sub-origin condition naming either compat
 * layer has to look the pick up under the canonical id or it never matches.
 */
class OriginConditionLayerAliasTest {

    private static final Identifier NEO_LAYER = Identifier.fromNamespaceAndPath("neoorigins", "origin");
    private static final Identifier HUMAN = Identifier.fromNamespaceAndPath("neoorigins", "human");

    private static OriginCondition parse(String layer) {
        return OriginCondition.parse(JsonParser.parseString(
            "{ \"type\": \"origins:origin\", \"layer\": \"" + layer + "\", \"origin\": \"neoorigins:human\" }")
            .getAsJsonObject());
    }

    @Test
    void theCompatOriginLayersAreReadAsTheNeoOriginsLayer() {
        Map<Identifier, Identifier> chosen = Map.of(NEO_LAYER, HUMAN);
        assertTrue(parse("origins:origin").test(chosen));
        assertTrue(parse("apace:origin").test(chosen));
        assertTrue(parse("neoorigins:origin").test(chosen));
    }

    @Test
    void otherLayersAreLeftAlone() {
        Map<Identifier, Identifier> chosen = Map.of(NEO_LAYER, HUMAN);
        assertFalse(parse("origins:class").test(chosen),
            "only the origin layer is folded; a foreign layer keeps its own id");
        assertTrue(parse("origins:class").test(Map.of(Identifier.fromNamespaceAndPath("origins", "class"), HUMAN)));
    }
}
