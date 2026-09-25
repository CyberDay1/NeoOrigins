package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The loader's block compiler (action_on_event, prevent_block_use, prevent_sleep,
 * block_collision) must tell blocks apart on {@code fluid}, {@code light_level},
 * {@code exposed_to_sky} and {@code movement_blocking}. They used to fall to its
 * match-all leaf, so every test here evaluates two positions and wants two answers.
 */
class LoaderBlockPredicateLeavesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos POS = BlockPos.ZERO;

    /** What the world holds at {@link #POS} for one evaluation. */
    private record Spot(Fluid fluid, int light, boolean sky, boolean blocksMotion) {}

    private static final Spot MATCHING = new Spot(Fluids.WATER, 3, true, true);
    private static final Spot OTHER = new Spot(Fluids.EMPTY, 13, false, false);

    private static ServerPlayer playerAt(Spot spot) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(Blocks.STONE);
        when(state.blocksMotion()).thenReturn(spot.blocksMotion());
        FluidState fluid = mock(FluidState.class);
        when(fluid.getType()).thenReturn(spot.fluid());
        when(fluid.isEmpty()).thenReturn(spot.fluid() == Fluids.EMPTY);

        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(POS)).thenReturn(state);
        when(level.getFluidState(POS)).thenReturn(fluid);
        when(level.getMaxLocalRawBrightness(POS)).thenReturn(spot.light());
        when(level.canSeeSky(POS)).thenReturn(spot.sky());

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        return player;
    }

    private static BiPredicate<ServerPlayer, BlockPos> compile(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return OriginsCompatPowerLoader.compileBlockPredicate(obj, "test:loader_leaves");
    }

    private static final String[] LEAVES = {
        "{\"type\": \"origins:fluid\", \"fluid_condition\": {\"type\": \"origins:fluid\", \"fluid\": \"minecraft:water\"}",
        "{\"type\": \"origins:light_level\", \"comparison\": \"<=\", \"compare_to\": 7",
        "{\"type\": \"origins:exposed_to_sky\"",
        "{\"type\": \"origins:movement_blocking\"",
    };

    /** Each leaf is written so MATCHING (wet, dark, open to the sky, solid) matches and OTHER does not. */
    @Test
    void eachLeafTellsTwoSpotsApart() {
        for (String leaf : LEAVES) {
            var pred = compile(leaf + "}");
            assertFalse(pred.test(playerAt(OTHER), POS), leaf + " matched OTHER");
            assertTrue(pred.test(playerAt(MATCHING), POS), leaf + " missed MATCHING");
        }
    }

    /** {@code inverted} must flip the answer exactly once, not cancel itself out. */
    @Test
    void invertedIsTheComplement() {
        for (String leaf : LEAVES) {
            var pred = compile(leaf + ", \"inverted\": true}");
            assertTrue(pred.test(playerAt(OTHER), POS), leaf + " inverted missed OTHER");
            assertFalse(pred.test(playerAt(MATCHING), POS), leaf + " inverted matched MATCHING");
        }
    }

    /** An unreadable {@code fluid} keeps this compiler's fail-open convention, with a warning. */
    @Test
    void anUnreadableFluidFailsOpenLikeEveryOtherUnreadableLeafHere() {
        var pred = compile("{\"type\": \"origins:fluid\"}");
        assertTrue(pred.test(playerAt(OTHER), POS));
        assertTrue(pred.test(playerAt(MATCHING), POS));
    }
}
