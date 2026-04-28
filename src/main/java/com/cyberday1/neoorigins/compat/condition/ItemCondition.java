package com.cyberday1.neoorigins.compat.condition;

import net.minecraft.world.item.ItemStack;

/**
 * Predicate over a single ItemStack — Apoli's "item condition" model.
 * Used by {@code equipped_item.item_condition}, {@code modify_inventory.
 * item_condition}, and standalone in any context where pack authors
 * want to gate behaviour on the contents of a specific stack.
 *
 * <p>Parallel to {@link EntityCondition} but evaluates against a stack
 * rather than a player. Composition (AND/OR/NOT) is supported via
 * {@code ItemConditionParser}; primitive variants check item id,
 * tag, NBT subtree containment, enchantment level, emptiness, etc.
 */
@FunctionalInterface
public interface ItemCondition {
    boolean test(ItemStack stack);

    static ItemCondition alwaysTrue()  { return s -> true; }
    static ItemCondition alwaysFalse() { return s -> false; }
}
