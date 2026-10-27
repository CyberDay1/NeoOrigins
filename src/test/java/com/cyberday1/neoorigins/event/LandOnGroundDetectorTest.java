package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code land} also fires from an on-ground rising edge, for landings that raise
 * no {@code LivingFallEvent} (creative flight, slow descents). A survival landing
 * that already fired through {@code LivingFallEvent} stamps the tick, and the
 * detector skips that tick so the event fires once.
 */
class LandOnGroundDetectorTest {

    private ServerPlayer sp;

    @AfterEach
    void clear() {
        if (sp != null) EventPowerIndex.invalidate(sp.getUUID());
    }

    private AtomicInteger countLands() {
        AtomicInteger lands = new AtomicInteger();
        EventPowerIndex.register(sp, EventPowerIndex.Event.LAND, (p, ctx) -> lands.incrementAndGet());
        return lands;
    }

    private void tick(int tickCount, boolean onGround) {
        sp.tickCount = tickCount;
        Mockito.when(sp.onGround()).thenReturn(onGround);
        PlayerLifecycleEvents.onPlayerTickPost(new PlayerTickEvent.Post(sp));
    }

    @Test
    void touchingDownWithoutAFallEventFiresLandOnce() {
        sp = PlayerLifecycle.player();
        AtomicInteger lands = countLands();

        tick(1, false);
        tick(2, true);
        tick(3, true);

        assertEquals(1, lands.get(), "one touchdown must fire land exactly once");

        tick(4, false);
        tick(5, true);
        assertEquals(2, lands.get(), "the next touchdown fires again");
    }

    @Test
    void aLandingTheFallEventAlreadyReportedIsNotRepeated() {
        sp = PlayerLifecycle.player();
        AtomicInteger lands = countLands();

        tick(1, false);
        EventPowerIndex.markLandDispatched(sp.getUUID(), 2);
        tick(2, true);

        assertEquals(0, lands.get(), "the detector double-fired a landing LivingFallEvent had already sent");
    }
}
