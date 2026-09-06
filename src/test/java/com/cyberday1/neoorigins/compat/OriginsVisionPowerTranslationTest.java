package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #121: Origins' {@code s} and {@code v} on {@code origins:lava_vision}
 * are absolute fog planes in blocks — {@code @ModifyConstant} replacements for
 * vanilla's lava fog start and end — not multipliers. Mapping {@code s} onto our
 * multiplicative {@code strength} turned the canonical {@code {"s": 0, "v": 15}}
 * into {@code strength: 0}, which blanked the screen, and discarded {@code v},
 * the field that actually carries the vision distance.
 *
 * <p>Also covers {@code origins:water_vision}, which was mapped onto
 * {@code neoorigins:lava_vision} and so did nothing at all in water.
 */
class OriginsVisionPowerTranslationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject translate(String id, String json) {
        Optional<JsonObject> out = OriginsPowerTranslator.translate(
            ResourceLocation.parse(id),
            JsonParser.parseString(json).getAsJsonObject());
        assertTrue(out.isPresent(), id + " did not translate");
        return out.orElseThrow();
    }

    /** The reporter's power, verbatim. */
    @Test
    void lavaVisionMapsSAndVToAbsolutePlanes() {
        JsonObject out = translate("compat:lava_vision", """
            {
              "name": "Lava Vision",
              "description": "You can see in lava.",
              "type": "origins:lava_vision",
              "s": 0,
              "v": 15
            }
            """);

        assertEquals("neoorigins:lava_vision", out.get("type").getAsString());
        assertEquals(0.0f, out.get("start").getAsFloat(), 1.0e-6);
        assertEquals(15.0f, out.get("end").getAsFloat(), 1.0e-6);
        assertFalse(out.has("strength"),
            "s is an absolute plane; writing it to the multiplier is what blanked the screen");
        assertEquals("Lava Vision", out.get("name").getAsString(), "display name must survive");
    }

    /** A bare Origins lava_vision carries no planes and must inherit our defaults. */
    @Test
    void lavaVisionWithoutFieldsEmitsNoPlanes() {
        JsonObject out = translate("compat:bare_lava_vision",
            "{ \"type\": \"origins:lava_vision\" }");

        assertEquals("neoorigins:lava_vision", out.get("type").getAsString());
        assertFalse(out.has("start"));
        assertFalse(out.has("end"));
        assertFalse(out.has("strength"));
    }

    /** Either plane on its own is legal upstream, so it has to survive alone here. */
    @Test
    void lavaVisionMapsEitherPlaneIndependently() {
        JsonObject onlyV = translate("compat:v_only",
            "{ \"type\": \"origins:lava_vision\", \"v\": 12.5 }");
        assertEquals(12.5f, onlyV.get("end").getAsFloat(), 1.0e-6);
        assertFalse(onlyV.has("start"));

        JsonObject onlyS = translate("compat:s_only",
            "{ \"type\": \"origins:lava_vision\", \"s\": 1.5 }");
        assertEquals(1.5f, onlyS.get("start").getAsFloat(), 1.0e-6);
        assertFalse(onlyS.has("end"));
    }

    /**
     * {@code origins:water_vision} is not a power type upstream at all: Origins'
     * own water_vision.json is a toggle_night_vision gated on being submerged in
     * water. Packs reference the bare id and expect to see clearly underwater.
     */
    @Test
    void waterVisionBecomesNightVisionGatedOnWater() {
        JsonObject out = translate("origins:water_vision",
            "{ \"type\": \"origins:simple\" }");

        assertEquals("neoorigins:night_vision", out.get("type").getAsString(),
            "water_vision routed through lava_vision, which reads the camera's fluid");

        JsonObject cond = out.getAsJsonObject("condition");
        assertEquals("neoorigins:submerged_in", cond.get("type").getAsString());
        assertEquals("minecraft:water", cond.get("fluid").getAsString());
    }

    /**
     * The two compat routes carry independent copies of the well-known Origins
     * id table, and water_vision was wrong in both. Pin them together so a fix
     * to one is not mistaken for a fix to the pack.
     *
     * <p>The loader now emits the canonical {@code persistent_effect} form
     * directly (it must survive the alias table's retirement); the translator
     * still leans on the legacy remap. Canonicalize the translator side the way
     * the load path would before comparing.
     */
    @Test
    void bothCompatRoutesAgreeOnWaterVision() throws Exception {
        var m = OriginsCompatPowerLoader.class.getDeclaredMethod("waterVisionJson");
        m.setAccessible(true);
        JsonObject loaderSide = (JsonObject) m.invoke(null);

        JsonObject translatorSide = translate("origins:water_vision",
            "{ \"type\": \"origins:simple\" }");
        com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases.bootstrap();
        com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases.simulateApply(
            ResourceLocation.parse(translatorSide.get("type").getAsString()),
            translatorSide, ResourceLocation.parse("test:water_vision"));

        assertEquals(translatorSide.get("type"), loaderSide.get("type"));
        assertEquals(translatorSide.get("condition"), loaderSide.get("condition"));
        assertEquals(translatorSide.get("effects"), loaderSide.get("effects"));
        assertEquals(translatorSide.get("toggleable"), loaderSide.get("toggleable"));
    }
}
