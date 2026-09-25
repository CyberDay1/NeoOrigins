package com.cyberday1.neoorigins.api.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A Nether spawn_location searches from the world spawn divided by 8, not from the raw coordinates. */
class SpawnSearchOriginTest {

    private static DimensionType scale(double s) {
        DimensionType type = mock(DimensionType.class);
        when(type.coordinateScale()).thenReturn(s);
        return type;
    }

    private static ServerLevel level(MinecraftServer server, double coordinateScale, int minY, int maxY) {
        DimensionType type = scale(coordinateScale);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server);
        when(level.dimensionType()).thenReturn(type);
        when(level.getMinBuildHeight()).thenReturn(minY);
        when(level.getMaxBuildHeight()).thenReturn(maxY);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        return level;
    }

    @Test
    void netherSearchesFromTheWorldSpawnDividedByEight() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = level(server, 1.0, -64, 320);
        ServerLevel nether = level(server, 8.0, 0, 256);
        when(server.overworld()).thenReturn(overworld);
        when(overworld.getSharedSpawnPos()).thenReturn(new BlockPos(8000, 70, -1600));

        assertEquals(new BlockPos(1000, 70, -200), LocationCondition.spawnSearchOrigin(nether));
    }

    @Test
    void overworldSearchesFromTheWorldSpawnUnchanged() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = level(server, 1.0, -64, 320);
        when(server.overworld()).thenReturn(overworld);
        BlockPos spawn = new BlockPos(8000, 70, -1600);
        when(overworld.getSharedSpawnPos()).thenReturn(spawn);

        assertSame(spawn, LocationCondition.spawnSearchOrigin(overworld));
    }

    @Test
    void sameScaleDimensionKeepsTheCoordinates() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = level(server, 1.0, -64, 320);
        ServerLevel end = level(server, 1.0, 0, 256);
        when(server.overworld()).thenReturn(overworld);
        when(overworld.getSharedSpawnPos()).thenReturn(new BlockPos(8000, 70, -1600));

        assertEquals(new BlockPos(8000, 70, -1600), LocationCondition.spawnSearchOrigin(end));
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
