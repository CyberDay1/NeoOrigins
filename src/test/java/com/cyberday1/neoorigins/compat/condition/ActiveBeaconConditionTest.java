package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code neoorigins:active_beacon} answers vanilla's own question: a beacon
 * grants effects only with a pyramid under it AND an unobstructed beam.
 *
 * <p>The beam alone is not that question. It comes from an upward sky scan that
 * never reads the base, and a beacon block is itself a beam block, so a bare
 * beacon under open sky has a beam section and no pyramid. The pyramid height is
 * read through {@code BeaconBlockEntityLevelsAccessor}, which a unit test cannot
 * stand up, so the gate is tested here and the field read is one line.
 */
class ActiveBeaconConditionTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @Test
    void bothHalvesAreRequired() {
        assertTrue(ConditionParser.beaconGrantsEffects(1, false), "pyramid and beam");
        assertFalse(ConditionParser.beaconGrantsEffects(0, false),
            "a bare beacon under open sky has a beam but grants nothing");
        assertFalse(ConditionParser.beaconGrantsEffects(4, true), "full pyramid, blocked beam");
        assertFalse(ConditionParser.beaconGrantsEffects(0, true), "neither");
    }

    @Test
    void aBlockThatIsNotABeaconNeverMatches() {
        Level level = mock(Level.class);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());
        var cond = ConditionParser.compileInBlockPredicate(
            JsonParser.parseString("{\"type\": \"neoorigins:active_beacon\"}").getAsJsonObject(), "test");
        assertFalse(cond.test(level, POS));
    }
}
