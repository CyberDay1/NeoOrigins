package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the invisible-wings report: Elytrian and Draconid glided with
 * nothing on their backs. The render layer and its capability plumbing were fine;
 * the shipped origins were simply wired to power types that could not ask for
 * wings, so nothing ever emitted {@code render_elytra}.
 *
 * <p>Two halves are pinned here, because fixing either alone leaves the bug
 * reachable: {@code natural_glide} / {@code flight} must be ABLE to emit the render
 * caps, and the built-in flight powers must actually SET the field. The second half
 * is the one that was broken, and no schema or golden-master gate covers it.
 *
 * <p>The defaults are pinned too, in the other direction: these two types shipped
 * for years without wings, so defaulting them on would put an elytra on the back of
 * every existing pack that used them.
 */
class FlightWingRenderCapsTest {

    private static final String RENDER = ElytraFlightPower.CAP_RENDER_ELYTRA;
    private static final String TEX = ElytraFlightPower.CAP_TEXTURE_PREFIX;

    private static Set<String> glideCaps(String json) {
        JsonObject obj = parseAndPinType(json, "neoorigins:natural_glide");
        var cfg = NaturalGlidePower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        return new NaturalGlidePower().capabilities(cfg);
    }

    private static Set<String> flightCaps(String json) {
        JsonObject obj = parseAndPinType(json, "neoorigins:flight");
        var cfg = FlightPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        return new FlightPower().capabilities(cfg);
    }

    /**
     * Parse, and pin the declared power type while doing it. Decoding alone will not
     * pin it: every Config CODEC reads {@code type} as an optional string defaulting
     * to empty, so a shipped file re-pointed at some other power type still decodes
     * cleanly through the codec named here, and every caps assertion below would stay
     * green while the origin quietly lost its wings in game. Being wired to a power
     * type that cannot ask for wings is the whole of #122, so a test that does not
     * pin the type is not testing the bug it was written for.
     */
    private static JsonObject parseAndPinType(String json, String expected) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(expected, obj.has("type") ? obj.get("type").getAsString() : null,
            "declared power type must stay " + expected);
        return obj;
    }

    /** Read one of the mod's own shipped power files off the test classpath. */
    private static String shipped(String powerFile) {
        String path = "/data/neoorigins/origins/powers/" + powerFile;
        try (InputStream in = FlightWingRenderCapsTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "shipped power file missing from the classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void naturalGlideDrawsNoWingsByDefault() {
        Set<String> caps = glideCaps("{ \"type\": \"neoorigins:natural_glide\" }");
        assertTrue(caps.contains("natural_glide"), "glide activation cap must always be emitted");
        assertFalse(caps.contains(RENDER),
            "natural_glide must default to no wings, or every existing pack using it sprouts an elytra");
    }

    @Test
    void flightDrawsNoWingsByDefault() {
        Set<String> caps = flightCaps("{ \"type\": \"neoorigins:flight\" }");
        assertTrue(caps.contains("flight"), "flight cap must always be emitted");
        assertFalse(caps.contains(RENDER), "flight must default to no wings for the same reason");
    }

    @Test
    void renderElytraOptsBothTypesIn() {
        assertTrue(glideCaps("{ \"type\": \"neoorigins:natural_glide\", \"render_elytra\": true }")
            .contains(RENDER), "natural_glide must be able to ask for wings");
        assertTrue(flightCaps("{ \"type\": \"neoorigins:flight\", \"render_elytra\": true }")
            .contains(RENDER), "flight must be able to ask for wings");
    }

    @Test
    void customTextureRidesTheCapabilitySetOnlyWhenWingsAreDrawn() {
        Set<String> on = glideCaps("""
            { "type": "neoorigins:natural_glide", "render_elytra": true,
              "texture_location": "mypack:textures/entity/my_wings.png" }
            """);
        assertTrue(on.contains(TEX + "mypack:textures/entity/my_wings.png"),
            "texture id must be encoded into the caps for the render layer to recover");

        Set<String> off = glideCaps("""
            { "type": "neoorigins:natural_glide", "render_elytra": false,
              "texture_location": "mypack:textures/entity/my_wings.png" }
            """);
        assertTrue(off.stream().noneMatch(c -> c.startsWith(TEX)),
            "a texture on an unrendered elytra must not leak into the caps");
    }

    /**
     * The actual bug. Every built-in origin that flies with an empty chest slot has
     * to opt in, or it glides bare-backed no matter how healthy the render code is.
     */
    @Test
    void shippedFlightOriginsAskForTheirWings() {
        for (String file : new String[] {
                "elytrian_flight.json", "phantom_flight.json", "hiveling_flight.json" }) {
            assertTrue(glideCaps(shipped(file)).contains(RENDER),
                file + " must set render_elytra, or that origin flies with no visible wings");
        }
        assertTrue(flightCaps(shipped("draconic_flight.json")).contains(RENDER),
            "draconic_flight.json must set render_elytra");
    }

    /**
     * Windwalker is the deliberate exception: its Sky Dancer glides on the wind
     * "as though borne on wings", so a literal elytra would be wrong. Pinned so a
     * later sweep doesn't switch it on for consistency.
     */
    @Test
    void windwalkerStaysWingless() {
        Set<String> caps = glideCaps(shipped("windwalker_sky_dancer.json"));
        assertTrue(caps.contains("natural_glide"), "Sky Dancer must still glide");
        assertFalse(caps.contains(RENDER), "Windwalker glides on the wind, not on an elytra");
    }

    @Test
    void elytraFlightStillDefaultsWingsOn() {
        JsonObject obj = JsonParser.parseString("{ \"type\": \"neoorigins:elytra_flight\" }")
            .getAsJsonObject();
        var cfg = ElytraFlightPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        Set<String> caps = new ElytraFlightPower().capabilities(cfg);
        assertTrue(caps.contains(RENDER),
            "elytra_flight mirrors Apoli, where wings are the norm; its default must stay true");
        assertEquals(2, caps.size(), "expected exactly natural_glide + render_elytra");
    }
}
