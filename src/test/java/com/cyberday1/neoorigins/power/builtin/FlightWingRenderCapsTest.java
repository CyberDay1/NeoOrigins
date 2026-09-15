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
 *
 * <p>{@code render_elytra} later widened from a boolean to the tri-state
 * {@link ElytraFlightPower.WingRender}, spelled as the SAME field so no pack had to
 * change. That is only true if both legacy spellings still decode to exactly what they
 * always meant, so all five accepted spellings are pinned below, alongside the rejection
 * of anything else — an author who mistypes a state must be told, not silently given
 * wings that never appear.
 */
class FlightWingRenderCapsTest {

    private static final String RENDER = ElytraFlightPower.CAP_RENDER_ELYTRA;
    private static final String ALWAYS = ElytraFlightPower.CAP_RENDER_ELYTRA_ALWAYS;
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

    private static Set<String> elytraFlightCaps(String json) {
        JsonObject obj = parseAndPinType(json, "neoorigins:elytra_flight");
        var cfg = ElytraFlightPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        return new ElytraFlightPower().capabilities(cfg);
    }

    /** Decode {@code render_elytra} through natural_glide's codec, error and all. */
    private static com.mojang.serialization.DataResult<NaturalGlidePower.Config> decodeGlide(String json) {
        return NaturalGlidePower.Config.CODEC.parse(
            JsonOps.INSTANCE, JsonParser.parseString(json).getAsJsonObject());
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
     * Elytrian is the one shipped origin whose wings are its body plan rather than a
     * flight effect, so it is the only one on {@code "always"}. Phantom / Hiveling /
     * Draconid keep the gliding-only wings, and pinning both halves is what stops a
     * later "consistency" pass from flipping the whole set either way.
     */
    @Test
    void onlyElytrianWearsItsWingsOnTheGround() {
        assertTrue(glideCaps(shipped("elytrian_flight.json")).contains(ALWAYS),
            "Elytrian's wings are its body plan: elytrian_flight.json must be render_elytra \"always\"");
        for (String file : new String[] { "phantom_flight.json", "hiveling_flight.json" }) {
            assertFalse(glideCaps(shipped(file)).contains(ALWAYS),
                file + " must keep wings to the glide only");
        }
        assertFalse(flightCaps(shipped("draconic_flight.json")).contains(ALWAYS),
            "draconic_flight.json must keep wings to the glide only");
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
        Set<String> caps = elytraFlightCaps("{ \"type\": \"neoorigins:elytra_flight\" }");
        assertTrue(caps.contains(RENDER),
            "elytra_flight mirrors Apoli, where wings are the norm; its default must stay true");
        assertFalse(caps.contains(ALWAYS),
            "the default is FLYING, not ALWAYS: true must keep meaning exactly what it meant");
        assertEquals(2, caps.size(), "expected exactly natural_glide + render_elytra");
    }

    /**
     * The widening contract. {@code render_elytra} went from boolean to tri-state under
     * the same field name, which is only safe if the two old spellings still decode to
     * the two old behaviours — {@code false} = NEVER, {@code true} = FLYING — and the
     * string spellings are exact synonyms of them. Checked on natural_glide because it
     * defaults to NEVER, so an explicit value is the only thing that can produce wings.
     */
    @Test
    void allFiveAcceptedSpellingsMeanWhatTheySay() {
        record Case(String value, boolean render, boolean always) {}
        for (Case c : new Case[] {
                new Case("false", false, false),
                new Case("\"never\"", false, false),
                new Case("true", true, false),
                new Case("\"flying\"", true, false),
                new Case("\"always\"", true, true) }) {
            Set<String> caps = glideCaps(
                "{ \"type\": \"neoorigins:natural_glide\", \"render_elytra\": " + c.value() + " }");
            assertEquals(c.render(), caps.contains(RENDER),
                "render_elytra " + c.value() + " should " + (c.render() ? "" : "not ") + "draw wings");
            assertEquals(c.always(), caps.contains(ALWAYS),
                "render_elytra " + c.value() + " should " + (c.always() ? "" : "not ")
                    + "keep the wings on outside a glide");
        }
    }

    /**
     * ALWAYS emits the OLD tag as well as the new one. The capability set is a wire
     * format read by several call sites written against the boolean; if "always" emitted
     * only its own tag, every one of them would read it as "no wings".
     */
    @Test
    void alwaysIsARefinementOfRenderNotAReplacement() {
        Set<String> caps = elytraFlightCaps(
            "{ \"type\": \"neoorigins:elytra_flight\", \"render_elytra\": \"always\" }");
        assertTrue(caps.contains(RENDER),
            "'always' must still emit render_elytra, or every boolean-era reader sees no wings");
        assertTrue(caps.contains(ALWAYS), "'always' must emit its own refinement tag");

        // A texture still rides along, same as it does for "flying".
        Set<String> textured = flightCaps("""
            { "type": "neoorigins:flight", "render_elytra": "always",
              "texture_location": "mypack:textures/entity/my_wings.png" }
            """);
        assertTrue(textured.contains(TEX + "mypack:textures/entity/my_wings.png"),
            "a custom texture must still be encoded when the wings are always on");
    }

    /**
     * A mistyped state must fail loudly. Silently falling back to NEVER would leave the
     * author staring at a bare back with nothing in the log to explain it, so the error
     * has to name the values that ARE legal.
     */
    @Test
    void anUnknownStringIsRejectedAndNamesTheLegalValues() {
        var bad = decodeGlide("{ \"type\": \"neoorigins:natural_glide\", \"render_elytra\": \"sometimes\" }");
        assertTrue(bad.error().isPresent(), "an unrecognised render_elytra value must not decode");
        String msg = bad.error().get().message();
        assertTrue(msg.contains("sometimes"), "the error must quote what the author wrote: " + msg);
        for (String legal : new String[] { "never", "flying", "always" }) {
            assertTrue(msg.contains(legal), "the error must name '" + legal + "': " + msg);
        }

        // The good values still decode through the same codec, so this is not a blanket reject.
        assertTrue(decodeGlide("{ \"type\": \"neoorigins:natural_glide\", \"render_elytra\": \"always\" }")
            .result().isPresent(), "'always' must still decode");
    }

    /**
     * The codec round-trips. {@code forGetter} has to produce something the same codec
     * can read back, or re-serialising a power (the editors do) would corrupt the field.
     * All three encode to their string spelling — including the two that came in as
     * booleans, which is a widening, not a change of meaning.
     */
    @Test
    void wingRenderRoundTripsThroughItsOwnCodec() {
        for (ElytraFlightPower.WingRender v : ElytraFlightPower.WingRender.values()) {
            var encoded = ElytraFlightPower.WingRender.CODEC.encodeStart(JsonOps.INSTANCE, v)
                .getOrThrow(m -> new AssertionError("encode failed for " + v + ": " + m));
            assertEquals(v.id(), encoded.getAsString(), v + " must encode to its string spelling");
            assertEquals(v, ElytraFlightPower.WingRender.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(m -> new AssertionError("decode failed for " + v + ": " + m)),
                v + " must survive a round trip");
        }
    }
}
