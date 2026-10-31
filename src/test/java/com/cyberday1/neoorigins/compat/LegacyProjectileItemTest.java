package com.cyberday1.neoorigins.compat;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fire_projectile tag's 1.20 Item compound, rebuilt as the stack it described. */
class LegacyProjectileItemTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x binds item components on datapack reload; a bare Bootstrap leaves them unbound.
        Items.LINGERING_POTION.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.SNOWBALL.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    private static CompoundTag snbt(String s) throws Exception {
        return TagParser.parseCompoundFully(s);
    }

    @Test
    void legacyPotionCompoundKeepsItsPotion() throws Exception {
        CompoundTag item = snbt("{id:\"minecraft:lingering_potion\",Count:1b,tag:{Potion:\"minecraft:poison\"}}");
        assertTrue(LegacyTagToComponents.isLegacyItemCompound(item));
        ItemStack stack = LegacyTagToComponents.legacyItemStack(item, null);
        assertEquals(Items.LINGERING_POTION, stack.getItem());
        assertEquals(1, stack.getCount());
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        assertTrue(contents != null && contents.is(Potions.POISON));
    }

    @Test
    void legacyCountCarriesOver() throws Exception {
        ItemStack stack = LegacyTagToComponents.legacyItemStack(snbt("{id:\"minecraft:snowball\",Count:3b}"), null);
        assertEquals(3, stack.getCount());
    }

    @Test
    void modernAndUnknownCompoundsAreLeftAlone() throws Exception {
        assertFalse(LegacyTagToComponents.isLegacyItemCompound(snbt(
            "{id:\"minecraft:lingering_potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:poison\"}}}")));
        assertFalse(LegacyTagToComponents.isLegacyItemCompound(snbt("{id:\"minecraft:snowball\"}")));
        assertTrue(LegacyTagToComponents.legacyItemStack(snbt("{id:\"nope:missing\",Count:1b}"), null).isEmpty());
    }
}
