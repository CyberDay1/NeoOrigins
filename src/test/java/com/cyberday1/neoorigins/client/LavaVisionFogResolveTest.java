package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.client.VisualEffectsHandler.LavaFog;
import com.cyberday1.neoorigins.power.builtin.LavaVisionPower;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #121: a compat {@code lava_vision} whose Origins {@code s} field was
 * mapped onto our multiplicative {@code strength} produced {@code strength: 0},
 * which scaled BOTH fog planes to zero: every fragment fully fogged, i.e. a
 * flat, featureless screen.
 *
 * <p>These tests drive the real capability strings the power emits through the
 * real resolver, so they cover the encode and the decode halves together, then
 * exercise the plane arithmetic the resolved values feed. Only the render event
 * itself needs a live client; every input that produced the whiteout is
 * rejected before it gets there.
 */
class LavaVisionFogResolveTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        ClientActivePowers.clear();
    }

    /** Encodes {@code json} through the real power and publishes its capabilities. */
    private static void grant(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        var decoded = LavaVisionPower.Config.CODEC.decode(JsonOps.INSTANCE, obj);
        assertTrue(decoded.result().isPresent(),
            "config did not decode: " + decoded.error().map(Object::toString).orElse("?"));
        LavaVisionPower.Config cfg = decoded.result().orElseThrow().getFirst();
        ClientActivePowers.set(Map.of(), new LavaVisionPower().capabilities(cfg));
    }

    private static void grantRaw(String... capabilities) {
        ClientActivePowers.set(Map.of(), Set.of(capabilities));
    }

    @Test
    void noPowerResolvesToNothing() {
        grantRaw("wall_climb", "overlay:minecraft:textures/misc/pumpkinblur.png:0.5");
        assertNull(VisualEffectsHandler.resolveLavaFog(),
            "an unrelated capability set must leave vanilla lava fog alone");
    }

    @Test
    void defaultStrengthResolvesToTheMultiplierAndNoAbsolutePlanes() {
        grant("{ \"type\": \"neoorigins:lava_vision\" }");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertEquals(3.0f, fog.multiplier(), 1.0e-6);
        assertTrue(Float.isNaN(fog.start()), "no start was authored, so none may be resolved");
        assertTrue(Float.isNaN(fog.end()), "no end was authored, so none may be resolved");
    }

    /**
     * The exact shape of the reporter's power once translated: {@code s: 0} and
     * {@code v: 15} become absolute planes, and the untouched default strength
     * must not be what drives the fog.
     */
    @Test
    void absolutePlanesSurviveEncodingIncludingAZeroStart() {
        grant("{ \"type\": \"neoorigins:lava_vision\", \"start\": 0.0, \"end\": 15.0 }");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertEquals(0.0f, fog.start(), 1.0e-6, "start 0 is legitimate: fog begins at the eye");
        assertEquals(15.0f, fog.end(), 1.0e-6, "end carries the actual vision distance");
    }

    /** Only the named plane goes absolute; the other still rides the multiplier. */
    @Test
    void oneAbsolutePlaneLeavesTheOtherUnset() {
        grant("{ \"type\": \"neoorigins:lava_vision\", \"end\": 20.0 }");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertTrue(Float.isNaN(fog.start()));
        assertEquals(20.0f, fog.end(), 1.0e-6);
        assertEquals(3.0f, fog.multiplier(), 1.0e-6);
    }

    // ---- Plane application. Vanilla's lava fog without fire resistance runs
    // 0.25 to 1.0 blocks (FogRenderer#setupFog), so those are the inputs here.

    /**
     * The documented contract: an absolute value wins for the plane it names,
     * and the plane with none still rides the multiplier. Resolving one plane
     * absolutely used to drop the multiplier from the other and leave it on
     * vanilla's raw value, which quietly narrowed vision for anyone authoring
     * {@code end} alone.
     */
    @Test
    void thePlaneWithNoAbsoluteValueStillRidesTheMultiplier() {
        float[] planes = VisualEffectsHandler.applyPlanes(new LavaFog(3.0f, Float.NaN, 20.0f), 0.25f, 1.0f);

        assertEquals(0.75f, planes[0], 1.0e-6, "no absolute start, so the multiplier applies");
        assertEquals(20.0f, planes[1], 1.0e-6, "the authored end wins outright");
    }

    @Test
    void bothAbsolutePlanesIgnoreTheMultiplierEntirely() {
        float[] planes = VisualEffectsHandler.applyPlanes(new LavaFog(3.0f, 0.0f, 15.0f), 0.25f, 1.0f);

        assertEquals(0.0f, planes[0], 1.0e-6);
        assertEquals(15.0f, planes[1], 1.0e-6);
    }

    @Test
    void noAbsolutePlanesScaleBothEnds() {
        float[] planes = VisualEffectsHandler.applyPlanes(new LavaFog(4.0f, Float.NaN, Float.NaN), 0.25f, 1.0f);

        assertEquals(1.0f, planes[0], 1.0e-6);
        assertEquals(4.0f, planes[1], 1.0e-6);
    }

    /** An end at or before the start is total fog at every distance. */
    @Test
    void invertedPlanesLeaveVanillaAlone() {
        assertNull(VisualEffectsHandler.applyPlanes(new LavaFog(3.0f, 10.0f, 5.0f), 0.25f, 1.0f),
            "an end nearer than the start would paint a flat screen");
        assertNull(VisualEffectsHandler.applyPlanes(new LavaFog(3.0f, 8.0f, 8.0f), 0.25f, 1.0f),
            "coincident planes are the same whiteout");
    }

    /**
     * The whiteout itself. A zero multiplier collapses both planes onto the
     * origin, so it must never reach the fog event.
     */
    @Test
    void zeroStrengthIsRejectedRatherThanApplied() {
        grant("{ \"type\": \"neoorigins:lava_vision\", \"strength\": 0.0 }");

        assertNull(VisualEffectsHandler.resolveLavaFog(),
            "strength 0 blanks the screen; vanilla fog must be left in place");
    }

    @Test
    void negativeAndNonFiniteStrengthAreRejected() {
        grant("{ \"type\": \"neoorigins:lava_vision\", \"strength\": -4.0 }");
        assertNull(VisualEffectsHandler.resolveLavaFog(), "a negative multiplier inverts the planes");

        grantRaw(LavaVisionPower.CAPABILITY_PREFIX + "Infinity:NaN:NaN");
        assertNull(VisualEffectsHandler.resolveLavaFog(), "an infinite multiplier has no usable planes");
    }

    /** A bad multiplier still yields fog when an absolute plane was authored. */
    @Test
    void zeroStrengthStillResolvesWhenAnAbsolutePlaneIsPresent() {
        grant("{ \"type\": \"neoorigins:lava_vision\", \"strength\": 0.0, \"end\": 15.0 }");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertEquals(15.0f, fog.end(), 1.0e-6);
        assertEquals(3.0f, fog.multiplier(), 1.0e-6,
            "the rejected 0 must fall back to the default, not survive as 0");
    }

    /**
     * Two lava_vision powers at once — from stacked origin tiers, say. The
     * combination has to be the most generous of each, never a value that
     * narrows one holder's vision because another power was also active.
     */
    @Test
    void multiplePowersCombineToTheMostGenerousPlanes() {
        grantRaw(
            LavaVisionPower.CAPABILITY_PREFIX + "4.0:2.0:10.0",
            LavaVisionPower.CAPABILITY_PREFIX + "8.0:0.5:25.0");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertEquals(8.0f, fog.multiplier(), 1.0e-6);
        assertEquals(0.5f, fog.start(), 1.0e-6, "nearest start wins");
        assertEquals(25.0f, fog.end(), 1.0e-6, "furthest end wins");
    }

    /** Legacy/hand-written packs may emit the bare tag with no payload. */
    @Test
    void bareCapabilityWithNoPayloadStillGrantsDefaultFog() {
        grantRaw("lava_vision");

        LavaFog fog = VisualEffectsHandler.resolveLavaFog();
        assertEquals(3.0f, fog.multiplier(), 1.0e-6);
        assertTrue(Float.isNaN(fog.start()));
        assertTrue(Float.isNaN(fog.end()));
    }

    /**
     * The encoding is fixed-arity on purpose: an absent absolute plane has to be
     * distinguishable from a supplied one, and NaN is the only float that
     * survives {@code toString}/{@code parseFloat} while meaning "unset".
     */
    @Test
    void capabilityEncodingKeepsAllThreeSlots() {
        JsonObject obj = JsonParser.parseString("{ \"type\": \"neoorigins:lava_vision\" }").getAsJsonObject();
        LavaVisionPower.Config cfg = LavaVisionPower.Config.CODEC
            .decode(JsonOps.INSTANCE, obj).result().orElseThrow().getFirst();

        String cap = new LavaVisionPower().capabilities(cfg).iterator().next();
        assertTrue(cap.startsWith(LavaVisionPower.CAPABILITY_PREFIX));
        String[] parts = cap.substring(LavaVisionPower.CAPABILITY_PREFIX.length()).split(":", -1);
        assertEquals(3, parts.length, "payload must stay strength:start:end");
        assertFalse(parts[0].isEmpty(), "an empty slot would parse as a silent NaN");
    }
}
