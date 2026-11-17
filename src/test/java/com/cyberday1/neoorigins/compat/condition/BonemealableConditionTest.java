package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code neoorigins:bonemealable} asks the block the question {@code grow} asks
 * before it rolls, so an ability gated on it skips the cost of a grow that cannot
 * work (issue #148). A crop that is still growing passes; a fully grown one, and
 * a block that never takes bone meal, do not.
 */
class BonemealableConditionTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    private static boolean test(BlockState state, String json) {
        Level level = mock(Level.class);
        when(level.getBlockState(POS)).thenReturn(state);
        return ConditionParser.compileInBlockPredicate(
            JsonParser.parseString(json).getAsJsonObject(), "test").test(level, POS);
    }

    private static boolean bonemealable(BlockState state) {
        return test(state, "{\"type\": \"neoorigins:bonemealable\"}");
    }

    @Test
    void aGrowingCropIsBonemealable() {
        assertTrue(bonemealable(Blocks.WHEAT.defaultBlockState()));
    }

    @Test
    void aFullyGrownCropIsNot() {
        BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
        assertFalse(bonemealable(ripe));
    }

    @Test
    void farmlandIsNot() {
        // What a visual-shape raycast hits when it passes through a crop.
        assertFalse(bonemealable(Blocks.FARMLAND.defaultBlockState()));
    }

    @Test
    void invertedNegatesIt() {
        assertTrue(test(Blocks.STONE.defaultBlockState(),
            "{\"type\": \"neoorigins:bonemealable\", \"inverted\": true}"));
    }
}
