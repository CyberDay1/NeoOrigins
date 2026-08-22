package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.compat.condition.AccessoryInspector;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A kept trinket whose Curios/Accessories slot isn't writable at respawn waits in
 * {@code pendingTrinketRestore} for a grace window instead of failing straight over
 * to the inventory, which drops the item when the inventory is full.
 *
 * <p>The retry itself needs a live ServerPlayer and a trinket mod, so it can only be
 * checked in-world. What is checked here is the queue's bookkeeping, and in
 * particular that a second death inside the window <em>accumulates</em>: the stash is
 * the only remaining reference to those stacks, so replacing the entry would destroy
 * the first death's trinkets outright.
 */
class PendingTrinketRetryTest {

    /** ItemStack construction touches data components, which need the registries bound. */
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @BeforeEach
    void clearQueue() {
        PlayerLifecycleEvents.pendingTrinketRestore.remove(PLAYER);
    }

    private static PlayerLifecycleEvents.KeptTrinket trinket(String slotId, int index, ItemStack stack) {
        return new PlayerLifecycleEvents.KeptTrinket(
            AccessoryInspector.Source.CURIOS, slotId, index, stack);
    }

    @Test
    void aTrinketWhoseSlotWasNotReadyIsQueuedRatherThanDropped() {
        var ring = trinket("ring", 0, new ItemStack(Items.DIAMOND));
        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(ring));

        var pending = PlayerLifecycleEvents.pendingTrinketRestore.get(PLAYER);
        assertNotNull(pending, "an unplaced trinket must be held for retry, not abandoned");
        assertEquals(1, pending.entries.size());
        assertSame(ring, pending.entries.get(0));
        assertTrue(pending.ticksLeft > 0, "the retry needs a grace window to run in");
    }

    /**
     * Dying again inside the grace window is ordinary — respawning into lava, or the
     * void. If the second defer replaced the first, the first death's trinkets would
     * be unreachable and gone for good.
     */
    @Test
    void asecondDeathInsideTheWindowAccumulatesInsteadOfDestroyingTheFirstStash() {
        var ring = trinket("ring", 0, new ItemStack(Items.DIAMOND));
        var belt = trinket("belt", 0, new ItemStack(Items.EMERALD));

        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(ring));
        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(belt));

        var pending = PlayerLifecycleEvents.pendingTrinketRestore.get(PLAYER);
        assertNotNull(pending);
        assertEquals(2, pending.entries.size(),
            "both deaths' trinkets must survive; a replacing put would leave 1 and "
                + "silently destroy the other");
        assertTrue(pending.entries.contains(ring), "the first death's trinket must still be queued");
        assertTrue(pending.entries.contains(belt));
    }

    /** The second death restarts the clock, so the newly queued stack gets a full window. */
    @Test
    void asecondDeathRefreshesTheGraceWindow() {
        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(trinket("ring", 0, new ItemStack(Items.DIAMOND))));
        var pending = PlayerLifecycleEvents.pendingTrinketRestore.get(PLAYER);
        pending.ticksLeft = 1;

        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(trinket("belt", 0, new ItemStack(Items.EMERALD))));

        assertTrue(pending.ticksLeft > 1,
            "a trinket queued on the second death must not inherit the first's nearly "
                + "spent window");
    }

    /** Nothing to defer must not leave an empty entry behind for the tick drain to walk. */
    @Test
    void everythingPlacedAtRespawnQueuesNothing() {
        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of());

        assertNull(PlayerLifecycleEvents.pendingTrinketRestore.get(PLAYER),
            "no queue entry when every trinket already went back to its own slot");
    }

    /**
     * NEGATIVE CONTROL. The queue is only meaningful if the entry list is mutable —
     * the tick drain removes placed trinkets from it in place via removeIf. If defer
     * ever stored the caller's immutable List.of() directly, the drain would throw
     * UnsupportedOperationException on the first successful retry.
     */
    @Test
    void theQueuedListIsMutableSoTheTickDrainCanRemovePlacedTrinkets() {
        PlayerLifecycleEvents.deferTrinkets(PLAYER, List.of(trinket("ring", 0, new ItemStack(Items.DIAMOND))));

        var pending = PlayerLifecycleEvents.pendingTrinketRestore.get(PLAYER);
        pending.entries.removeIf(t -> true);

        assertTrue(pending.entries.isEmpty(), "the drain must be able to empty the queue in place");
    }
}
