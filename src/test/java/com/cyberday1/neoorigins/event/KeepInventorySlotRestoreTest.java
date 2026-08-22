package com.cyberday1.neoorigins.event;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the report "keep_inventory drops items/curios on the ground when
 * your inventory is full". Kept items should come back in the slots they died in.
 *
 * <p>The old restore called {@code Inventory.add(stack)}, which only ever fills the
 * main inventory (0-35). With a full inventory and {@code slots: ["*"]}, death
 * stashed all 41 slots, {@code add} refilled 0-35, and the four armour stacks plus
 * the offhand had nowhere left to go, so they were dropped on the ground. Restoring
 * by index fixes both halves of that: armour and offhand can reach their own slots,
 * and nothing has to compete for space.
 */
class KeepInventorySlotRestoreTest {

    /** ItemStack construction touches data components, which need the registries bound. */
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x moved item default components out of registration and into a
        // datapack-reload step (ReloadableServerResources binds them via
        // DataComponentInitializers), so after a bare Bootstrap every Item holder
        // is unbound and `new ItemStack(...)` throws "Components not bound yet".
        // These tests read no components -- they only need non-empty stacks -- so
        // an empty default map is enough. Running the real initializer pass
        // outside a reload is what NeoForge's component validator rejects.
        for (Item item : BuiltInRegistries.ITEM) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    /** Vanilla player inventory geometry: 0-35 main, 36-39 armour, 40 offhand. */
    private static final int INVENTORY_SIZE = 41;
    private static final int FIRST_ARMOR_SLOT = 36;
    private static final int OFFHAND_SLOT = 40;

    /** A container whose main inventory (0-35) is completely full, armour/offhand empty. */
    private static SimpleContainer fullMainInventory() {
        var inv = new SimpleContainer(INVENTORY_SIZE);
        for (int i = 0; i < FIRST_ARMOR_SLOT; i++) {
            inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        return inv;
    }

    @Test
    void armorReturnsToItsOwnSlotEvenWhenTheMainInventoryIsFull() {
        var inv = fullMainInventory();
        var helmet = new ItemStack(Items.DIAMOND_HELMET);

        assertTrue(PlayerLifecycleEvents.restoreToOriginalSlot(inv, FIRST_ARMOR_SLOT, helmet),
            "a free armour slot must accept its own stack back");
        assertEquals(helmet, inv.getItem(FIRST_ARMOR_SLOT));
    }

    @Test
    void offhandReturnsToItsOwnSlotEvenWhenTheMainInventoryIsFull() {
        var inv = fullMainInventory();
        var shield = new ItemStack(Items.SHIELD);

        assertTrue(PlayerLifecycleEvents.restoreToOriginalSlot(inv, OFFHAND_SLOT, shield));
        assertEquals(shield, inv.getItem(OFFHAND_SLOT));
    }

    /**
     * NEGATIVE CONTROL. Reproduces what {@code Inventory.add} did: first free slot in
     * 0-35 only. If this ever succeeds the test above proves nothing, because it would
     * mean the old code could already have placed the item correctly.
     */
    @Test
    void firstFreeSlotPlacementCannotPlaceArmorAtAll_soTheFixIsLoadBearing() {
        var inv = fullMainInventory();

        int firstFree = -1;
        for (int i = 0; i < FIRST_ARMOR_SLOT; i++) {
            if (inv.getItem(i).isEmpty()) { firstFree = i; break; }
        }

        assertEquals(-1, firstFree,
            "main inventory is full, so the old add()-based restore had nowhere to put "
                + "armour or the offhand and dropped it on the ground");
    }

    @Test
    void everySlotGoesBackWhereItCameFromRatherThanBeingCompacted() {
        var inv = new SimpleContainer(INVENTORY_SIZE);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        var boots = new ItemStack(Items.DIAMOND_BOOTS);

        assertTrue(PlayerLifecycleEvents.restoreToOriginalSlot(inv, 7, sword));
        assertTrue(PlayerLifecycleEvents.restoreToOriginalSlot(inv, 22, boots));

        assertEquals(sword, inv.getItem(7), "slot 7 must hold the stack that died in slot 7");
        assertEquals(boots, inv.getItem(22), "slot 22 must hold the stack that died in slot 22");
        assertTrue(inv.getItem(0).isEmpty(), "nothing may be compacted to the front");
        assertTrue(inv.getItem(1).isEmpty());
    }

    @Test
    void anOccupiedSlotIsRefusedSoTheCallerCanFallBack() {
        var inv = new SimpleContainer(INVENTORY_SIZE);
        inv.setItem(4, new ItemStack(Items.DIRT));

        assertFalse(PlayerLifecycleEvents.restoreToOriginalSlot(inv, 4, new ItemStack(Items.DIAMOND)),
            "an occupied slot must be refused rather than overwritten");
        assertTrue(inv.getItem(4).is(Items.DIRT), "the occupying stack must survive");
    }

    @Test
    void outOfRangeAndEmptyInputsAreRefused() {
        var inv = new SimpleContainer(INVENTORY_SIZE);

        assertFalse(PlayerLifecycleEvents.restoreToOriginalSlot(inv, -1, new ItemStack(Items.DIAMOND)));
        assertFalse(PlayerLifecycleEvents.restoreToOriginalSlot(inv, INVENTORY_SIZE, new ItemStack(Items.DIAMOND)));
        assertFalse(PlayerLifecycleEvents.restoreToOriginalSlot(inv, 5, ItemStack.EMPTY),
            "an empty stash entry must not occupy a slot");
    }
}
