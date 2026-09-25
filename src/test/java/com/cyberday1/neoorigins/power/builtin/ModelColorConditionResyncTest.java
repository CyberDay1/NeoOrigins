package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * A conditional model_color tint must resync the client when its condition
 * turns off, even when the player carries other conditional tints. Caveborn
 * carries six, one per ore meal; the tint of an expired meal stayed on.
 */
class ModelColorConditionResyncTest {

    private final ModelColorPower power = new ModelColorPower();

    private static ModelColorPower.Config tint(AtomicBoolean on) {
        return new ModelColorPower.Config(0.3f, 0.8f, 0.4f, 1.0f,
            Optional.of(p -> on.get()), true, "neoorigins:model_color");
    }

    /** Ticks every tint once, in order, and returns how many syncs were sent. */
    private int tickAll(ServerPlayer sp, List<ModelColorPower.Config> tints) {
        AtomicInteger syncs = new AtomicInteger();
        try (MockedStatic<NeoOriginsNetwork> net = mockStatic(NeoOriginsNetwork.class)) {
            net.when(() -> NeoOriginsNetwork.syncActivePowersToPlayer(any()))
                .then(inv -> { syncs.incrementAndGet(); return null; });
            for (ModelColorPower.Config c : tints) power.onTick(sp, c);
        }
        return syncs.get();
    }

    private void assertExpiryResyncs(List<ModelColorPower.Config> tints, AtomicBoolean meal) {
        ServerPlayer sp = PlayerLifecycle.player();
        tickAll(sp, tints);
        meal.set(true);
        assertTrue(tickAll(sp, tints) > 0, "turning on must resync");
        tickAll(sp, tints);
        meal.set(false);
        assertTrue(tickAll(sp, tints) > 0, "turning off must resync, or the tint stays on the client");
    }

    @Test
    void singleTintClearsWhenItsConditionEnds() {
        AtomicBoolean meal = new AtomicBoolean();
        assertExpiryResyncs(List.of(tint(meal)), meal);
    }

    @Test
    void tintClearsWhenAnotherTintIsTickedBeforeIt() {
        AtomicBoolean meal = new AtomicBoolean();
        assertExpiryResyncs(List.of(tint(new AtomicBoolean()), tint(meal)), meal);
    }

    @Test
    void tintClearsWhenAnotherTintIsTickedAfterIt() {
        AtomicBoolean meal = new AtomicBoolean();
        assertExpiryResyncs(List.of(tint(meal), tint(new AtomicBoolean())), meal);
    }

    @Test
    void steadyStateDoesNotResyncEverySecond() {
        AtomicBoolean meal = new AtomicBoolean(true);
        List<ModelColorPower.Config> tints = List.of(tint(meal), tint(new AtomicBoolean()));
        ServerPlayer sp = PlayerLifecycle.player();
        tickAll(sp, tints);
        assertTrue(tickAll(sp, tints) == 0, "an unchanged tint must not resync");
    }
}
