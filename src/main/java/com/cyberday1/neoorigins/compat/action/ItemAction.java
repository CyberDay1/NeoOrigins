package com.cyberday1.neoorigins.compat.action;

import net.minecraft.world.item.ItemStack;

/**
 * Operates on a single ItemStack — Apoli's "item action" model. Parallel
 * to {@link EntityAction} (player-targeted) but the target is a stack
 * already in some inventory slot. Used by {@code equipped_item_action}
 * and {@code modify_inventory} to let pack authors mutate items in
 * place (NBT merges, count changes, enchantment edits, etc.) without
 * needing one-off Java code per behaviour.
 *
 * <p>Implementations should mutate the stack directly. The caller is
 * responsible for ensuring the result is reflected back into the
 * inventory if the implementation returns a different instance — but
 * since {@link ItemStack} is mutated in place by Mojang's API for
 * count / data-component / damage edits, in-place mutation is the
 * common case.
 */
@FunctionalInterface
public interface ItemAction {
    void execute(ItemStack stack);

    static ItemAction noop() { return s -> {}; }
}
