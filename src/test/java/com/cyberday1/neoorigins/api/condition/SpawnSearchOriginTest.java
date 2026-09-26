package com.cyberday1.neoorigins.api.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A spawn_location search starts from the world spawn, scaled from the dimension
 * the world spawn is in to the one being searched, as a portal would. It used to
 * start from (0, 0) whatever the world spawn was.
 */
class SpawnSearchOriginTest {

    private final MinecraftServer server = mock(MinecraftServer.class);
    private final ServerLevel overworld = level(1.0, -64, 319);
    private final ServerLevel nether = level(8.0, 0, 255);

    private ServerLevel level(double coordinateScale, int minY, int maxY) {
        DimensionType type = mock(DimensionType.class);
        when(type.coordinateScale()).thenReturn(coordinateScale);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server);
        when(level.dimensionType()).thenReturn(type);
        when(level.getMinY()).thenReturn(minY);
        when(level.getMaxY()).thenReturn(maxY);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        return level;
    }

    /** The world spawn at {@code pos}, set in {@code in}. The recorded key is not read; the resolved level is. */
    private void worldSpawn(ServerLevel in, BlockPos pos) {
        when(server.overworld()).thenReturn(overworld);
        when(server.getRespawnData()).thenReturn(LevelData.RespawnData.of(Level.OVERWORLD, pos, 0f, 0f));
        when(server.findRespawnDimension()).thenReturn(in);
    }

    @Test
    void netherSearchesFromTheWorldSpawnDividedByEight() {
        worldSpawn(overworld, new BlockPos(8000, 70, -1600));
        assertEquals(new BlockPos(1000, 70, -200), LocationCondition.spawnSearchOrigin(nether));
    }

    @Test
    void overworldSearchesFromTheWorldSpawnUnchanged() {
        worldSpawn(overworld, new BlockPos(2000, 70, 3000));
        assertEquals(new BlockPos(2000, 70, 3000), LocationCondition.spawnSearchOrigin(overworld));
    }

    @Test
    void worldSpawnInTheNetherIsUsedAsIsForTheNether() {
        BlockPos spawn = new BlockPos(250, 70, 375);
        worldSpawn(nether, spawn);
        assertSame(spawn, LocationCondition.spawnSearchOrigin(nether));
    }

    @Test
    void worldSpawnInTheNetherIsScaledUpForTheOverworld() {
        worldSpawn(nether, new BlockPos(250, 70, 375));
        assertEquals(new BlockPos(2000, 70, 3000), LocationCondition.spawnSearchOrigin(overworld));
    }

    @Test
    void worldSpawnInAThirdDimensionIsScaledByItsOwnCoordinateScale() {
        worldSpawn(level(4.0, 0, 255), new BlockPos(100, 70, -100));
        assertEquals(new BlockPos(50, 70, -50), LocationCondition.spawnSearchOrigin(nether));
        assertEquals(new BlockPos(400, 70, -400), LocationCondition.spawnSearchOrigin(overworld));
    }

    @Test
    void sameScaleDimensionKeepsTheCoordinates() {
        worldSpawn(overworld, new BlockPos(8000, 70, -1600));
        assertEquals(new BlockPos(8000, 70, -1600), LocationCondition.spawnSearchOrigin(level(1.0, 0, 255)));
    }

    @Test
    void spawnOutsideTheNetherBuildBandIsClamped() {
        WorldBorder border = new WorldBorder();
        assertEquals(new BlockPos(10, 255, 20),
            LocationCondition.scaleSpawn(new BlockPos(80, 300, 160), 0.125, 0, 255, border));
        assertEquals(new BlockPos(10, 0, 20),
            LocationCondition.scaleSpawn(new BlockPos(80, -50, 160), 0.125, 0, 255, border));
    }

    @Test
    void negativeCoordinatesRoundTowardTheBlockBelow() {
        assertEquals(new BlockPos(-2, 64, -1),
            LocationCondition.scaleSpawn(new BlockPos(-9, 64, -1), 0.125, 0, 255, new WorldBorder()));
    }

    @Test
    void resultStaysInsideTheWorldBorder() {
        WorldBorder border = new WorldBorder();
        border.setSize(100);
        BlockPos p = LocationCondition.scaleSpawn(new BlockPos(8000, 64, 0), 0.125, 0, 255, border);
        assertEquals(49, p.getX());
    }
}
