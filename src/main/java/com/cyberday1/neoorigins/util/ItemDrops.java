package com.cyberday1.neoorigins.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3 removed {@code drop(stack, randomly, thrownFromHand)}; the remaining
 * {@code drop} always throws forward and swings the arm. This keeps the old
 * scatter path: no arm swing, no ItemTossEvent, no drop stat.
 */
// hub: neoorigins/port-26-3.md
public final class ItemDrops {

    private ItemDrops() {}

    public static void spawn(LivingEntity entity, ItemStack stack, boolean randomly, boolean thrownFromHand) {
        ItemEntity item = entity.createItemStackToDrop(stack, randomly, thrownFromHand);
        if (item == null) return;
        var captured = entity.captureDrops();
        if (captured != null) captured.add(item);
        else entity.level().addFreshEntity(item);
    }
}
