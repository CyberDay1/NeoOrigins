package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code neoorigins:predicate} reads {@code offsetX}/{@code offsetY}/{@code offsetZ}
 * beside {@code predicate}, as vanilla {@code location_check} does (issue #151).
 * The player stands with feet in block (0, 64, 0); the world only answers for the
 * block one above, (0, 65, 0), so a predicate matches only with {@code offsetY} 1.
 */
class PredicateOffsetTest {

    private static final BlockPos ABOVE = new BlockPos(0, 65, 0);
    // The default water state is falling=true, so pin it.
    private static final FluidState STILL_WATER =
        Fluids.WATER.defaultFluidState().setValue(FlowingFluid.FALLING, false);

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.setPos(0.5, 64.0, 0.5);
        ServerLevel level = (ServerLevel) sp.level();
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.canSeeSky(any(BlockPos.class))).thenAnswer(i -> ABOVE.equals(i.getArgument(0)));
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(i ->
            ABOVE.equals(i.getArgument(0)) ? Blocks.SNOW.defaultBlockState() : Blocks.AIR.defaultBlockState());
        when(level.getFluidState(any(BlockPos.class))).thenAnswer(i ->
            ABOVE.equals(i.getArgument(0)) ? STILL_WATER : Fluids.EMPTY.defaultFluidState());
        @SuppressWarnings("unchecked")
        HolderOwner<Biome> owner = mock(HolderOwner.class);
        Holder<Biome> plains = Holder.Reference.createStandAlone(owner, Biomes.PLAINS);
        Holder<Biome> desert = Holder.Reference.createStandAlone(owner, Biomes.DESERT);
        // doAnswer: calling getBiome on the deep stub would try to mock the sealed Holder (26.3).
        doAnswer(i -> ABOVE.equals(i.getArgument(0)) ? plains : desert).when(level).getBiome(any(BlockPos.class));
        return sp;
    }

    private static boolean test(String type, String predicate, String offsets) {
        String json = "{\"type\": \"neoorigins:predicate\", \"predicate_type\": \"" + type
            + "\", \"predicate\": " + predicate + offsets + "}";
        return ConditionParser.parse(JsonParser.parseString(json).getAsJsonObject(), "test").test(player());
    }

    private static final String SKY = "{\"can_see_sky\": true}";

    @Test
    void locationMatchesOnlyWithTheOffset() {
        assertFalse(test("location", SKY, ""));
        assertFalse(test("location", SKY, ", \"offsetY\": 0"));
        assertTrue(test("location", SKY, ", \"offsetY\": 1"));
        assertFalse(test("location", SKY, ", \"offsetY\": 1, \"offsetX\": 1"));
    }

    @Test
    void blockStateFollowsTheOffset() {
        String snow = "{\"state\": {\"layers\": \"1\"}}";
        assertFalse(test("block_state", snow, ""));
        assertTrue(test("block_state", snow, ", \"offsetY\": 1"));
    }

    @Test
    void fluidStateFollowsTheOffset() {
        String water = "{\"state\": {\"falling\": \"false\"}}";
        assertFalse(test("fluid_state", water, ""));
        assertTrue(test("fluid_state", water, ", \"offsetY\": 1"));
    }

    @Test
    void biomeFollowsTheOffset() {
        String plains = "{\"biomes\": [\"minecraft:plains\"]}";
        assertFalse(test("biome", plains, ""));
        assertTrue(test("biome", plains, ", \"offsetY\": 1"));
    }

    @Test
    void aWholeDecimalCountsAndAFractionFailsClosed() {
        assertTrue(test("location", SKY, ", \"offsetY\": 1.0"));
        assertFalse(test("location", SKY, ", \"offsetY\": 1.5"));
        assertFalse(test("location", SKY, ", \"offsetY\": \"1\""));
        assertTrue(test("location", "{\"can_see_sky\": false}", ""));
        assertFalse(test("location", "{\"can_see_sky\": false}", ", \"offsetY\": 0.5"));
    }

    @Test
    void entityPropertiesAndItemIgnoreOffsets() {
        assertTrue(test("entity_properties", "{}", ""));
        assertTrue(test("entity_properties", "{}", ", \"offsetY\": 1"));
        // An empty main hand: the item predicate still reads it, offset or not.
        assertTrue(test("item", "{\"count\": 0}", ""));
        assertTrue(test("item", "{\"count\": 0}", ", \"offsetY\": 5"));
        assertFalse(test("item", "{\"count\": 1}", ", \"offsetY\": 5"));
    }
}
