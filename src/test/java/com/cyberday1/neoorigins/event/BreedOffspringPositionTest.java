package com.cyberday1.neoorigins.event;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * BabyEntitySpawnEvent fires before vanilla moves the offspring to its parent, so the
 * child (and any twin copied from it) still sits at (0,0,0). The handler must place
 * the child at parent A before BREED powers and Twin Births look at its position.
 */
class BreedOffspringPositionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void childIsMovedToParentBeforeDispatch() {
        Mob parentA = Mockito.mock(Mob.class);
        Mockito.when(parentA.getX()).thenReturn(120.5);
        Mockito.when(parentA.getY()).thenReturn(64.0);
        Mockito.when(parentA.getZ()).thenReturn(-33.5);
        Mockito.when(parentA.getYRot()).thenReturn(90f);
        AgeableMob child = Mockito.mock(AgeableMob.class, Mockito.RETURNS_DEEP_STUBS);
        ServerPlayer player = Mockito.mock(ServerPlayer.class, Mockito.RETURNS_DEEP_STUBS);

        BabyEntitySpawnEvent event = Mockito.mock(BabyEntitySpawnEvent.class);
        Mockito.when(event.getCausedByPlayer()).thenReturn(player);
        Mockito.when(event.getChild()).thenReturn(child);
        Mockito.when(event.getParentA()).thenReturn(parentA);

        try {
            WorldPowerEvents.onBabyEntitySpawn(event);
        } catch (RuntimeException ignored) {
            // The power lookups after the pre-position need a live player; only the
            // positioning step is under test here.
        }
        Mockito.verify(child).moveTo(120.5, 64.0, -33.5, 90f, 0.0F);
    }
}
