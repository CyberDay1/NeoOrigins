package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ElytraFlightPower;
import com.cyberday1.neoorigins.power.builtin.NaturalGlidePower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each {@code render_elytra} spelling actually resolves to on the wire, and
 * where the shipped origins land.
 *
 * <p>The tri-state is encoded as capability tags — {@code render_elytra} plus, for
 * {@code "always"}, {@code render_elytra_always} — and
 * {@link NeoOriginsNetwork#rendersElytraFrom} / {@code alwaysRendersElytraFrom} read
 * them back out into the two booleans the sync payload carries. Everything below
 * goes through the real codec, the real {@code capabilities(config)} and those real
 * readers, so a change to any link in that chain shows up here.
 *
 * <p>The second half runs over the mod's own shipped powers loaded into the real
 * {@code PowerDataManager}, which is what makes "the Elytrian origin lands on
 * always" a measured fact rather than a reading of the JSON.
 */
// hub: neoorigins/elytra-draw-gate.md
class RenderElytraResolutionTest {

    private static final String NS = "neoorigins";

    @BeforeAll
    static void loadRealData() {
        RealDatapack.load();
    }

    // ── the four spellings ───────────────────────────────────────────────

    private static Set<String> capsForElytraFlight(String renderElytraLiteral) {
        String json = "{\"type\":\"neoorigins:elytra_flight\",\"render_elytra\":"
            + renderElytraLiteral + "}";
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        DataResult<?> decoded = ElytraFlightPower.Config.CODEC.parse(JsonOps.INSTANCE, obj);
        assertTrue(decoded.error().isEmpty(),
            () -> "render_elytra " + renderElytraLiteral + " failed to decode: "
                + decoded.error().map(e -> e.message()).orElse("?"));
        ElytraFlightPower.Config config = (ElytraFlightPower.Config) decoded.result().orElseThrow();
        return new ElytraFlightPower().capabilities(config);
    }

    private static void assertResolves(String literal, boolean render, boolean always) {
        Set<String> caps = capsForElytraFlight(literal);
        assertEquals(render, NeoOriginsNetwork.rendersElytraFrom(caps),
            "render_elytra " + literal + " -> render flag");
        assertEquals(always, NeoOriginsNetwork.alwaysRendersElytraFrom(caps),
            "render_elytra " + literal + " -> always flag");
    }

    @Test
    void neverDrawsNothing() {
        assertResolves("\"never\"", false, false);
        assertResolves("false", false, false);
    }

    @Test
    void theLegacyBooleanAndFlyingAreTheSameState() {
        assertResolves("true", true, false);
        assertResolves("\"flying\"", true, false);
        assertEquals(capsForElytraFlight("true"), capsForElytraFlight("\"flying\""),
            "a legacy boolean true must keep meaning exactly \"flying\"");
    }

    @Test
    void alwaysDrawsOutsideAGlideAndStillSetsThePlainFlag() {
        // "always" must emit the old tag too: every reader written against the
        // boolean has to keep working, and the draw gate asks both questions.
        assertResolves("\"always\"", true, true);
    }

    // ── the shipped origins ──────────────────────────────────────────────

    private static Set<String> shippedPowerCaps(String powerId) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(
            ResourceLocation.fromNamespaceAndPath(NS, powerId));
        assertNotNull(holder, powerId + " is not a loaded power");
        return capsOf(holder);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> capsOf(PowerHolder<?> holder) {
        return ((com.cyberday1.neoorigins.api.power.PowerType) holder.type())
            .capabilities(holder.config());
    }

    @Test
    void theElytrianOriginLandsOnAlways() {
        assertTrue(OriginDataManager.INSTANCE.getOrigin(
            ResourceLocation.fromNamespaceAndPath(NS, "elytrian")).powers()
            .contains(ResourceLocation.fromNamespaceAndPath(NS, "elytrian_flight")),
            "the Elytrian origin must still grant elytrian_flight");

        Set<String> caps = shippedPowerCaps("elytrian_flight");
        assertTrue(NeoOriginsNetwork.rendersElytraFrom(caps), "Elytrian draws wings");
        assertTrue(NeoOriginsNetwork.alwaysRendersElytraFrom(caps), "Elytrian wings stay on");
    }

    @Test
    void theOtherFlyingOriginsStayGlideOnly() {
        for (String power : new String[]{"phantom_flight", "hiveling_flight", "draconic_flight"}) {
            Set<String> caps = shippedPowerCaps(power);
            assertTrue(NeoOriginsNetwork.rendersElytraFrom(caps), power + " draws wings");
            assertFalse(NeoOriginsNetwork.alwaysRendersElytraFrom(caps),
                power + " must only draw while gliding — widening it is a visual change nobody asked for");
        }
    }

    @Test
    void windwalkerStaysWingless() {
        // Sky Dancer declares no render_elytra at all. It stays wingless only because
        // NaturalGlidePower defaults to NEVER while ElytraFlightPower defaults to
        // FLYING; unifying those defaults would silently put wings on the Windwalker.
        Set<String> caps = shippedPowerCaps("windwalker_sky_dancer");
        assertFalse(NeoOriginsNetwork.rendersElytraFrom(caps),
            "the Windwalker glides on robes, not wings");

        DataResult<?> bare = NaturalGlidePower.Config.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"type\":\"neoorigins:natural_glide\"}").getAsJsonObject());
        NaturalGlidePower.Config config = (NaturalGlidePower.Config) bare.result().orElseThrow();
        assertEquals(ElytraFlightPower.WingRender.NEVER, config.renderElytra(),
            "natural_glide must keep defaulting to NEVER");
    }
}
