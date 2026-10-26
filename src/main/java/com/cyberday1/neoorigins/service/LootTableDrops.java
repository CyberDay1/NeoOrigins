package com.cyberday1.neoorigins.service;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which of a dying entity's captured drops came from its loot table.
 *
 * <p>{@code LivingDropsEvent} hands over one list holding the loot-table roll, the
 * custom death loot (mob equipment with drop chances, horse armor) and
 * {@code dropEquipment} (saddles, chests, horse and llama inventories) with nothing
 * to tell them apart. {@code LivingEntityLootTableDropsMixin} snapshots the list
 * right after the loot-table roll and clears it when {@code dropAllDeathLoot}
 * returns; the drops event fires in between, on the same thread.
 */
public final class LootTableDrops {

    private static final Map<LivingEntity, Set<ItemEntity>> BY_ENTITY =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private LootTableDrops() {}

    public static void mark(LivingEntity entity, Collection<ItemEntity> captured) {
        Set<ItemEntity> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(captured);
        BY_ENTITY.put(entity, set);
    }

    /** The loot-table drops of {@code entity}'s death in progress; empty if none was recorded. */
    public static Set<ItemEntity> of(LivingEntity entity) {
        Set<ItemEntity> set = BY_ENTITY.get(entity);
        return set == null ? Set.of() : set;
    }

    public static void clear(LivingEntity entity) {
        BY_ENTITY.remove(entity);
    }
}
