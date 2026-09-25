package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code inverted} on a block condition means the same thing under on_block as on every other path. */
class OnBlockInvertedTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos POS = BlockPos.ZERO;
    private static final String STONE_INVERTED =
        "{ \"type\": \"origins:block\", \"block\": \"minecraft:stone\", \"inverted\": true }";
    private static final String STONE =
        "{ \"type\": \"origins:block\", \"block\": \"minecraft:stone\" }";

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static BlockState stateOf(Block block) {
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        return state;
    }

    /** On ground, {@code feet} at the player's position and {@code below} underfoot. */
    private static ServerPlayer player(Block feet, Block below, boolean onGround) {
        BlockState here = stateOf(feet);
        BlockState under = stateOf(below);
        // ServerPlayer.level() narrows to ServerLevel on 26.x.
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(POS)).thenReturn(here);
        when(level.getBlockState(POS.below())).thenReturn(under);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(POS);
        when(player.onGround()).thenReturn(onGround);
        return player;
    }

    private static ServerPlayer on(Block below) {
        return player(Blocks.AIR, below, true);
    }

    private static boolean onBlock(String blockCondition, ServerPlayer player) {
        return ConditionParser.parse(obj(
            "{ \"type\": \"origins:on_block\", \"block_condition\": " + blockCondition + " }"), "test:on_block")
            .test(player);
    }

    private static boolean wrapped(String type, String blockCondition, ServerPlayer player) {
        return ConditionParser.parse(obj(
            "{ \"type\": \"" + type + "\", \"block_condition\": " + blockCondition + " }"), "test:" + type)
            .test(player);
    }

    private static boolean loader(String blockCondition, ServerPlayer player) {
        return OriginsCompatPowerLoader.compileBlockPredicate(obj(blockCondition), "test:loader")
            .test(player, player.blockPosition().below());
    }

    // ── the measured table ───────────────────────────────────────────────

    @Test
    void onBlockHonoursInverted() {
        assertFalse(onBlock(STONE_INVERTED, on(Blocks.STONE)),
            "on stone, `not stone` must be false; it used to ignore the flag and answer true");
    }

    @Test
    void onBlockInvertedStillMatchesOtherBlocks() {
        assertTrue(onBlock(STONE_INVERTED, on(Blocks.DIRT)), "on dirt, `not stone` must be true");
    }

    @Test
    void theOtherPathsStillNegate() {
        ServerPlayer inStone = player(Blocks.STONE, Blocks.STONE, true);
        assertFalse(wrapped("origins:in_block", STONE_INVERTED, inStone), "in_block");
        assertFalse(wrapped("origins:block", STONE_INVERTED, inStone), "block");
        assertFalse(loader(STONE_INVERTED, on(Blocks.STONE)), "loader compiler");
    }

    @Test
    void nonInvertedControlsStillMatch() {
        ServerPlayer inStone = player(Blocks.STONE, Blocks.STONE, true);
        assertTrue(onBlock(STONE, on(Blocks.STONE)), "on_block");
        assertTrue(wrapped("origins:in_block", STONE, inStone), "in_block");
        assertTrue(wrapped("origins:block", STONE, inStone), "block");
        assertTrue(loader(STONE, on(Blocks.STONE)), "loader compiler");
    }

    @Test
    void invertedDoesNotTurnOnBlockIntoAirborne() {
        assertFalse(onBlock(STONE_INVERTED, player(Blocks.AIR, Blocks.DIRT, false)),
            "the flag negates the block, not the on-ground requirement");
    }

    @Test
    void onBlockHonoursInvertedOnATag() {
        ServerPlayer onStone = on(Blocks.STONE);
        when(onStone.level().getBlockState(POS.below()).is(BlockTags.BASE_STONE_OVERWORLD)).thenReturn(true);
        String tag = "{ \"type\": \"origins:in_tag\", \"tag\": \"minecraft:base_stone_overworld\"";
        assertTrue(onBlock(tag + " }", onStone));
        assertFalse(onBlock(tag + ", \"inverted\": true }", onStone));
    }

    // ── nesting ──────────────────────────────────────────────────────────

    @Test
    void invertedOnAnOrNode() {
        String cond = "{ \"type\": \"origins:or\", \"inverted\": true, \"conditions\": ["
            + STONE + ", { \"type\": \"origins:block\", \"block\": \"minecraft:dirt\" } ] }";
        assertFalse(onBlock(cond, on(Blocks.STONE)));
        assertTrue(onBlock(cond, on(Blocks.OAK_PLANKS)));
    }

    @Test
    void invertedOnAnAndNode() {
        String cond = "{ \"type\": \"origins:and\", \"inverted\": true, \"conditions\": [" + STONE + "] }";
        assertFalse(onBlock(cond, on(Blocks.STONE)));
        assertTrue(onBlock(cond, on(Blocks.DIRT)));
    }

    @Test
    void invertedChildInsideAnOr() {
        String cond = "{ \"type\": \"origins:or\", \"conditions\": ["
            + STONE_INVERTED + ", { \"type\": \"origins:block\", \"block\": \"minecraft:dirt\" } ] }";
        assertFalse(onBlock(cond, on(Blocks.STONE)), "neither `not stone` nor `dirt` holds on stone");
        assertTrue(onBlock(cond, on(Blocks.DIRT)));
    }

    @Test
    void invertedChildInsideAnAnd() {
        String cond = "{ \"type\": \"origins:and\", \"conditions\": [" + STONE_INVERTED + "] }";
        assertFalse(onBlock(cond, on(Blocks.STONE)));
        assertTrue(onBlock(cond, on(Blocks.DIRT)));
    }

    @Test
    void invertedTwoLevelsDown() {
        String cond = "{ \"type\": \"origins:any_of\", \"conditions\": [ { \"type\": \"origins:all_of\","
            + " \"conditions\": [" + STONE_INVERTED + "] } ] }";
        assertFalse(onBlock(cond, on(Blocks.STONE)));
        assertTrue(onBlock(cond, on(Blocks.DIRT)));
    }
}
