package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A `block` condition written with a root-level `tag` instead of `block` tests tag membership. */
class BlockRootTagTest {

    private static final TagKey<Block> MARKED = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("test", "marked"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindTag() {
        BuiltInRegistries.BLOCK.bindTags(Map.of(MARKED, List.of(Blocks.STONE.builtInRegistryHolder())));
    }

    @AfterEach
    void unbindTags() {
        BuiltInRegistries.BLOCK.resetTags();
    }

    @Test
    void entityBlockConditionReadsRootTag() {
        var json = json("{\"type\": \"origins:block\", \"tag\": \"test:marked\"}");
        EntityCondition cond = ConditionParser.parseBlockCondition(json, "test");
        assertTrue(cond.test(standingIn(Blocks.STONE)), "a block in the tag must match");
        assertFalse(cond.test(standingIn(Blocks.DIRT)), "a block outside the tag must not match");
    }

    @Test
    void nestedBlockConditionReadsRootTag() {
        var cond = ConditionParser.compileInBlockPredicate(
            json("{\"type\": \"origins:block\", \"tag\": \"#test:marked\"}"), "test");
        assertNotNull(cond, "a block node with a root tag did not compile");
        ServerPlayer stone = standingIn(Blocks.STONE);
        ServerPlayer dirt = standingIn(Blocks.DIRT);
        assertTrue(cond.test(stone.level(), BlockPos.ZERO), "a block in the tag must match");
        assertFalse(cond.test(dirt.level(), BlockPos.ZERO), "a block outside the tag must not match");
    }

    private static ServerPlayer standingIn(Block block) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(block.defaultBlockState());
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(BlockPos.ZERO);
        return player;
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
