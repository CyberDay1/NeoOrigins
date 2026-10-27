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
    }

    @Test
    void anAxeFellsTheTree() {
        assertTrue(WorldPowerEvents.fellsTrees(new ItemStack(Items.IRON_AXE), ItemStack.EMPTY));
        assertTrue(WorldPowerEvents.fellsTrees(new ItemStack(Items.NETHERITE_AXE), ItemStack.EMPTY));
    }

    @Test
    void anythingElseOnlyBreaksTheLog() {
        assertFalse(WorldPowerEvents.fellsTrees(ItemStack.EMPTY, ItemStack.EMPTY), "bare hand");
        assertFalse(WorldPowerEvents.fellsTrees(new ItemStack(Items.STONE_PICKAXE), ItemStack.EMPTY), "pickaxe");
        assertFalse(WorldPowerEvents.fellsTrees(new ItemStack(Items.OAK_LOG), ItemStack.EMPTY), "a block");
    }

    @Test
    void axeHandsCountOnlyWithAnEmptyHand() {
        ItemStack axeHands = new ItemStack(Items.IRON_AXE);
        assertTrue(WorldPowerEvents.fellsTrees(ItemStack.EMPTY, axeHands));
        assertFalse(WorldPowerEvents.fellsTrees(new ItemStack(Items.STICK), axeHands));
    }
}
