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

    /**
     * 26.x stores dust colour as a packed RGB int, so a float channel is floored
     * to 8 bits on the way in ({@code ARGB.as8BitChannel}) and can read back up
     * to 1/255 low. The 1.21.1 branch kept a raw {@code Vector3f} and could
     * assert to 1e-6.
     */
    private static final float CHANNEL = 1.0F / 255.0F;

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
        assertEquals(0.1f, dust.getColor().x(), CHANNEL);
        assertEquals(0.5f, dust.getColor().y(), CHANNEL);
        assertEquals(0.1f, dust.getColor().z(), CHANNEL);
        assertEquals(1.0f, dust.getScale(), 1.0e-6);

        // The other 1.20 inline forms.
        BlockParticleOption block = assertInstanceOf(BlockParticleOption.class,
            decode("\"minecraft:block minecraft:stone\"").particle());
        assertEquals(Blocks.STONE.defaultBlockState(), block.getState());

        // 26.x: ItemParticleOption carries an ItemStackTemplate, not an ItemStack.
        ItemParticleOption item = assertInstanceOf(ItemParticleOption.class,
            decode("\"minecraft:item minecraft:apple\"").particle());
        assertEquals(Items.APPLE, item.getItem().item().value());
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
}
