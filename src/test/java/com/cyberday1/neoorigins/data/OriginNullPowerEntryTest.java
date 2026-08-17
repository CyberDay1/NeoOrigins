package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the failure a live server reported on 2026-08-14:
 *
 * <pre>Failed to parse origin neoorigins:derek: Not a string: null; Not a string:
 * null; Not a string: null; Not a string: null; Not a string: null</pre>
 *
 * <p>Five identical messages, no key name, no index — and the origin silently
 * absent from the picker. The cause is null entries in a power-id list: one null
 * fails the whole {@link Origin} codec, so every power the author actually named
 * is lost with it. The origin file itself was never recovered, so these fixtures
 * reconstruct the shape rather than the file.
 */
class OriginNullPowerEntryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static final String HEAD =
        "\"id\": \"neoorigins:derek\", \"name\": \"Derek\", \"description\": \"A person.\"";

    /**
     * The reproduction. If a future DFU bump starts naming the offending key or
     * index, this is what tells us the message in the bug report can finally be
     * acted on directly.
     */
    @Test
    void nullPowerEntriesFailTheWholeOriginWithAnAnonymousMessage() {
        DataResult<Origin> result = Origin.CODEC.parse(JsonOps.INSTANCE,
            json("{" + HEAD + ", \"powers\": [null, null, null, null, null]}"));

        assertTrue(result.error().isPresent(), "a null power entry must fail the codec");
        assertFalse(result.result().isPresent(), "the whole origin is lost, not just the entry");
        assertEquals(
            "Not a string: null; Not a string: null; Not a string: null; "
                + "Not a string: null; Not a string: null",
            result.error().orElseThrow().message(),
            "this is the exact message the 2026-08-14 server log carried");
    }

    /** The fix: the named powers survive, only the nulls are dropped. */
    @Test
    void strippingKeepsEveryPowerTheAuthorNamed() {
        JsonObject origin = json("{" + HEAD
            + ", \"powers\": [\"neoorigins:fly\", null, \"neoorigins:swim\", null]}");

        assertEquals(2, OriginDataManager.stripNullPowerEntries(origin));

        Origin parsed = Origin.CODEC.parse(JsonOps.INSTANCE, origin).result().orElseThrow();
        assertEquals(
            List.of(ResourceLocation.parse("neoorigins:fly"), ResourceLocation.parse("neoorigins:swim")),
            parsed.powers());
    }

    /** Every list that carries power ids is covered, not just the top-level one. */
    @Test
    void strippingCoversUpgradesAndTierOverlays() {
        JsonObject origin = json("{" + HEAD + ","
            + "\"powers\": [null],"
            + "\"upgrades\": [null],"
            + "\"tier_powers\": ["
            + "  {\"tier\": 1, \"add\": [\"neoorigins:fly\", null], \"remove\": [null, null]}"
            + "]}");

        assertEquals(5, OriginDataManager.stripNullPowerEntries(origin));

        Origin parsed = Origin.CODEC.parse(JsonOps.INSTANCE, origin).result().orElseThrow();
        assertEquals(List.of(ResourceLocation.parse("neoorigins:fly")),
            parsed.powersForTier(1));
    }

    /** A clean origin must come through byte-identical — the strip is never noise. */
    @Test
    void aCleanOriginIsUntouched() {
        JsonObject origin = json("{" + HEAD + ", \"powers\": [\"neoorigins:fly\"]}");
        String before = origin.toString();

        assertEquals(0, OriginDataManager.stripNullPowerEntries(origin));
        assertEquals(before, origin.toString());
    }
}
