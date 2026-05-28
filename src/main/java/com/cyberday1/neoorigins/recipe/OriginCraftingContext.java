package com.cyberday1.neoorigins.recipe;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holder for the {@link Player} associated with an in-progress
 * crafting check.
 *
 * <p>Vanilla's {@link net.minecraft.world.item.crafting.Recipe#matches} signature
 * does not carry a player reference (it takes only the {@code RecipeInput} and
 * {@code Level}). To gate matches on per-player origin state without forking
 * every vanilla recipe path, we plant the player here via a mixin on
 * {@link net.minecraft.world.inventory.CraftingMenu#slotsChanged} (and the
 * server-side recipe-lookup ticks of an open menu) and read it back from
 * {@link OriginGatedRecipe#matches}.
 *
 * <p>The slot is per-thread because crafting evaluation is single-threaded
 * within the server tick — the same thread that drives {@code slotsChanged}
 * also calls {@code Recipe.matches}. A null current value means "no per-player
 * gate available, fail closed" — the recipe behaves as if the gate failed,
 * which is the desired conservative default.
 */
public final class OriginCraftingContext {

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    private OriginCraftingContext() {}

    public static void push(Player player) {
        CURRENT.set(player);
    }

    public static void pop() {
        CURRENT.remove();
    }

    @Nullable
    public static Player current() {
        return CURRENT.get();
    }
}
