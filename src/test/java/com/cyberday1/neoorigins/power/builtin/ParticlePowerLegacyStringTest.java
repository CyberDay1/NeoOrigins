package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #118: fairytale's forest_stealth writes
 * {@code "particle": "minecraft:dust 0.1 0.5 0.1 1"} — the 1.20 command form,
 * with the parameters inline in the string. The power used to reject the whole
 * config over that one cosmetic field ("missing or unknown 'particle' field"),
 * taking the origin down with it.
 */
class ParticlePowerLegacyStringTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ParticlePower.Config decode(String particleField) {
        var json = JsonParser.parseString(
            "{ \"type\": \"neoorigins:particle\", \"particle\": " + particleField + " }");
        var result = ParticlePower.Config.CODEC.decode(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(),
            "decode failed: " + result.error().map(Object::toString).orElse("?"));
        return result.result().orElseThrow().getFirst();
    }

    /** Anchored on the exact field from fairytale:forest_stealth_particles. */
    @Test
    void legacyInlineArgumentStringsParse() {
        DustParticleOptions dust = assertInstanceOf(DustParticleOptions.class,
            decode("\"minecraft:dust 0.1 0.5 0.1 1\"").particle());
        assertEquals(0.1f, dust.getColor().x(), 1.0e-6);
        assertEquals(0.5f, dust.getColor().y(), 1.0e-6);
        assertEquals(0.1f, dust.getColor().z(), 1.0e-6);
        assertEquals(1.0f, dust.getScale(), 1.0e-6);

        // The other 1.20 inline forms.
        BlockParticleOption block = assertInstanceOf(BlockParticleOption.class,
            decode("\"minecraft:block minecraft:stone\"").particle());
        assertEquals(Blocks.STONE.defaultBlockState(), block.getState());

        ItemParticleOption item = assertInstanceOf(ItemParticleOption.class,
            decode("\"minecraft:item minecraft:apple\"").particle());
        assertEquals(Items.APPLE, item.getItem().getItem());

    }

    /**
     * The pre-existing bare-id and object shapes must keep working, and a
     * cosmetic field must never sink the power: unreadable arguments degrade to
     * the head token rather than failing the config outright.
     */
    @Test
    void existingShapesSurviveAndBadArgumentsDegradeToTheHeadToken() {
        assertEquals(ParticleTypes.END_ROD, decode("\"minecraft:end_rod\"").particle());
        assertInstanceOf(DustParticleOptions.class,
            decode("{ \"type\": \"minecraft:dust\", \"color\": [1.0, 0.85, 0.2], \"scale\": 0.6 }").particle());
        assertEquals(ParticleTypes.FLAME, decode("\"minecraft:flame 0.1 0.5 0.1\"").particle());
        assertEquals(ParticleTypes.SOUL, decode("\"minecraft:soul   \"").particle());
    }

    /**
     * A bare particle id must resolve on every MC version the mod supports, even
     * where that version happens to make the particle parameterized.
     *
     * <p>MC 26 reclassified dragon_breath, effect, instant_effect and flash from
     * SimpleParticleType to parameterized types. A 1.21.1 pack naming any of them
     * lost the whole power on 26.x with "missing or unknown 'particle' field" —
     * mrt_chemist:immunity-shot was the reported case. Asserting on the resolved
     * ParticleType rather than the options object keeps this one test valid on
     * every branch.
     */
    @Test
    void bareIdsResolveForParameterizedParticles() {
        assertEquals(ParticleTypes.DRAGON_BREATH, decode("\"minecraft:dragon_breath\"").particle().getType());
        assertEquals(ParticleTypes.EFFECT, decode("\"minecraft:effect\"").particle().getType());
        assertEquals(ParticleTypes.INSTANT_EFFECT, decode("\"minecraft:instant_effect\"").particle().getType());
        assertEquals(ParticleTypes.FLASH, decode("\"minecraft:flash\"").particle().getType());

        // Parameterized on every supported version, and equally unusable bare
        // before this: purely decorative options, so they get a plain default.
        assertEquals(ParticleTypes.ENTITY_EFFECT, decode("\"minecraft:entity_effect\"").particle().getType());
        assertEquals(ParticleTypes.DUST, decode("\"minecraft:dust\"").particle().getType());
        assertEquals(ParticleTypes.SCULK_CHARGE, decode("\"minecraft:sculk_charge\"").particle().getType());
        assertEquals(ParticleTypes.SHRIEK, decode("\"minecraft:shriek\"").particle().getType());
    }

    /**
     * The other half of the rule: a particle that needs a referent rather than a
     * decoration has no honest default, so it stays an error and the author gets
     * told to supply one.
     */
    @Test
    void particlesNeedingAReferentStillRequireArguments() {
        for (String id : new String[] { "minecraft:block", "minecraft:item", "minecraft:vibration" }) {
            var json = JsonParser.parseString(
                "{ \"type\": \"neoorigins:particle\", \"particle\": \"" + id + "\" }");
            assertTrue(ParticlePower.Config.CODEC.decode(JsonOps.INSTANCE, json).error().isPresent(),
                id + " should not resolve without arguments");
        }
    }
}
