package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.service.LootTableDrops;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scavenging doubles an animal's drops. Vanilla captures the loot-table roll and
 * {@code dropEquipment} (a pig's saddle, a horse's armor, a llama's chest and its
 * contents) into the same drops list, so copying the whole list duplicated gear.
 * Only stacks recorded as loot-table drops may be copied.
 */
class ScavengingEquipmentDupeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // 26.x: a bare Bootstrap leaves item components unbound.
        for (var item : java.util.List.of(Items.PORKCHOP, Items.SADDLE, Items.CHEST)) {
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        }
    }

    @Test
    void copiesLootButNotTheSaddle() {
        ItemEntity pork = drop(new ItemStack(Items.PORKCHOP, 2));
        ItemEntity saddle = drop(new ItemStack(Items.SADDLE));
        LivingEntity pig = Mockito.mock(LivingEntity.class);

        // Snapshot taken after the loot-table roll, before dropEquipment adds the saddle.
        LootTableDrops.mark(pig, List.of(pork));
        try {
            List<ItemStack> copies = CombatPowerEvents.scavengedCopies(
                List.of(pork, saddle), LootTableDrops.of(pig), 1);
            assertEquals(1, copies.size(), "only the loot-table stack may be copied");
            assertTrue(copies.get(0).is(Items.PORKCHOP));
            assertEquals(2, copies.get(0).getCount());
        } finally {
            LootTableDrops.clear(pig);
        }
    }

    @Test
    void copiesNothingWhenNoLootTableRollWasRecorded() {
        ItemEntity chest = drop(new ItemStack(Items.CHEST));
        LivingEntity llama = Mockito.mock(LivingEntity.class);
        assertTrue(CombatPowerEvents.scavengedCopies(List.of(chest), LootTableDrops.of(llama), 1).isEmpty());
    }

    private static ItemEntity drop(ItemStack stack) {
        ItemEntity e = Mockito.mock(ItemEntity.class);
        Mockito.when(e.getItem()).thenReturn(stack);
        return e;
    }
}
