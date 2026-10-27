package com.cyberday1.neoorigins.event;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timber! says "breaking a log with an axe": it used to fell the tree whatever broke
 * the log, a bare fist or a pickaxe included.
 */
class TreeFellingAxeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // A stack cannot be built while its item's components are unbound; these
        // tests read nothing but the item, so an empty map is enough.
        for (var item : new net.minecraft.world.item.Item[] { Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
                Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.STONE_PICKAXE, Items.OAK_LOG, Items.STICK }) {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    /** Item tags are not loaded in unit tests, so the vanilla axes stand in for #minecraft:axes. */
    private static final java.util.Set<net.minecraft.world.item.Item> AXES =
        java.util.Set.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);

    private static boolean fells(ItemStack mainHand, ItemStack bareHandTool) {
        return WorldPowerEvents.fellsTrees(mainHand, bareHandTool, s -> AXES.contains(s.getItem()));
    }

    @Test
    void anAxeFellsTheTree() {
        assertTrue(fells(new ItemStack(Items.IRON_AXE), ItemStack.EMPTY));
        assertTrue(fells(new ItemStack(Items.NETHERITE_AXE), ItemStack.EMPTY));
    }

    @Test
    void anythingElseOnlyBreaksTheLog() {
        assertFalse(fells(ItemStack.EMPTY, ItemStack.EMPTY), "bare hand");
        assertFalse(fells(new ItemStack(Items.STONE_PICKAXE), ItemStack.EMPTY), "pickaxe");
        assertFalse(fells(new ItemStack(Items.OAK_LOG), ItemStack.EMPTY), "a block");
    }

    @Test
    void axeHandsCountOnlyWithAnEmptyHand() {
        ItemStack axeHands = new ItemStack(Items.IRON_AXE);
        assertTrue(fells(ItemStack.EMPTY, axeHands));
        assertFalse(fells(new ItemStack(Items.STICK), axeHands));
    }
}
