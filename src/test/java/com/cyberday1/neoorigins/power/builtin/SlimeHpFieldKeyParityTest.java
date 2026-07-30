package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slime HP fields are authored through the in-game creator and the generated
 * schema, both of which emit the {@link FieldSpec#name()} declared in
 * {@link BuiltinPowers}. The codecs used to parse {@code split_max_hp} /
 * {@code levels_per_hp} / {@code max_bonus_hp} while those FieldSpecs read
 * {@code split_max_h_p} / {@code levels_per_h_p} / {@code max_bonus_h_p} — the
 * camel&rarr;snake of the {@code *HP}-suffixed Config record components. Editing
 * the field in the GUI therefore wrote a key nothing parsed, and the value
 * silently fell back to the default.
 *
 * <p>These tests take the key straight from the FieldSpec (i.e. exactly what the
 * GUI writes) and push it through the codec, so they fail if the two ever drift
 * apart again rather than merely asserting today's spelling.
 */
class SlimeHpFieldKeyParityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The author-facing JSON key {@link BuiltinPowers} advertises for a field. */
    private static String guiKey(String powerType, String componentSuffix) {
        FieldSpec spec = BuiltinPowers.fieldsFor(powerType).stream()
            .filter(f -> f.name().replace("_", "").equalsIgnoreCase(componentSuffix))
            .findFirst()
            .orElse(null);
        assertNotNull(spec, "no FieldSpec resembling '" + componentSuffix + "' on " + powerType);
        return spec.name();
    }

    private static <C> C decode(Codec<C> codec, JsonObject json) {
        var result = codec.decode(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(),
            "decode failed: " + result.error().map(Object::toString).orElse("?"));
        return result.result().orElseThrow().getFirst();
    }

    @Test
    void slimeLevelHpParsesTheKeysTheEditorEmits() {
        String levelsPer = guiKey("neoorigins:slime_level_hp", "levelsperhp");
        String maxBonus = guiKey("neoorigins:slime_level_hp", "maxbonushp");

        // Guard the exact spelling too — a rename to the h_p form would resurrect
        // the bug while still satisfying the round-trip below.
        assertEquals("levels_per_hp", levelsPer);
        assertEquals("max_bonus_hp", maxBonus);

        JsonObject json = JsonParser.parseString("{ \"type\": \"neoorigins:slime_level_hp\" }")
            .getAsJsonObject();
        json.addProperty(levelsPer, 3);
        json.addProperty(maxBonus, 7);

        SlimeLevelHPPower.Config cfg = decode(SlimeLevelHPPower.Config.CODEC, json);

        // Defaults are 10 / 20 — reading 3 / 7 back proves the key was consumed.
        assertEquals(3, cfg.levelsPerHp(), "editor key '" + levelsPer + "' did not reach the codec");
        assertEquals(7, cfg.maxBonusHp(), "editor key '" + maxBonus + "' did not reach the codec");
    }

    @Test
    void slimeDeathSaveParsesTheKeyTheEditorEmits() {
        String splitMax = guiKey("neoorigins:slime_death_save", "splitmaxhp");
        assertEquals("split_max_hp", splitMax);

        JsonObject json = JsonParser.parseString("{ \"type\": \"neoorigins:slime_death_save\" }")
            .getAsJsonObject();
        json.addProperty(splitMax, 9.0F);

        SlimeDeathSavePower.Config cfg = decode(SlimeDeathSavePower.Config.CODEC, json);

        // Default is 4.0 — reading 9.0 back proves the key was consumed.
        assertEquals(9.0F, cfg.splitMaxHp(), 1.0e-6,
            "editor key '" + splitMax + "' did not reach the codec");
    }
}
