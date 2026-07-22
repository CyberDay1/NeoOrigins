package com.cyberday1.neoorigins.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A crafting recipe wrapper that delegates to an {@code inner} crafting recipe
 * but additionally requires the crafting {@link Player} (read from
 * {@link OriginCraftingContext}) to satisfy every gate in {@link #gates()}.
 *
 * <p>Both load-time visibility and recipe-book sync work as if this were a
 * normal recipe: it ships to clients via the recipe-book payload, shows in JEI
 * / REI alongside vanilla recipes, and only the {@link #matches} call
 * short-circuits to {@code false} when the player fails any gate.
 *
 * <p>When evaluated off the crafting tick (e.g. recipe-book auto-fill from a
 * datapack reload before any menu is open), {@link OriginCraftingContext#current()}
 * returns {@code null} and we fall back to <em>deny</em> — the recipe will not
 * match. This is the conservative default and avoids leaking gated outputs to
 * non-qualifying players via auto-craft helpers.
 *
 * <p>The recipe form on disk is:
 * <pre>
 * {
 *   "type": "neoorigins:origin_gated_crafting",
 *   "gates": [ { "type": "neoorigins:has_origin", "origin": "neoorigins:human" } ],
 *   "inner": { "type": "minecraft:crafting_shaped", ... }
 * }
 * </pre>
 *
 * <p>{@code inner} must be a {@link CraftingRecipe}; cooking variants are not
 * yet supported by this serializer (see {@code docs/RECIPE_CONDITIONS.md}).
 */
public final class OriginGatedRecipe implements CraftingRecipe {

    private final List<OriginGate> gates;
    private final CraftingRecipe inner;

    public OriginGatedRecipe(List<OriginGate> gates, CraftingRecipe inner) {
        this.gates = List.copyOf(gates);
        this.inner = inner;
    }

    public List<OriginGate> gates() { return gates; }
    public CraftingRecipe inner()    { return inner; }

    private boolean passesGates(Player player) {
        if (player == null) return false;
        for (OriginGate gate : gates) {
            if (!gate.test(player)) return false;
        }
        return true;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // If the player can't be resolved (e.g. recipe-book preflight, server
        // startup recipe sanity check), default to deny so gated outputs never
        // leak. The CraftingMenu mixin plants the player just before every
        // slotsChanged-triggered match call, so real craft attempts hit the
        // happy path.
        Player player = OriginCraftingContext.current();
        if (!passesGates(player)) return false;
        return inner.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return inner.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return inner.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return inner.getResultItem(registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return inner.getIngredients();
    }

    /**
     * Delegate leftover/remainder computation to {@code inner}. The default
     * {@link CraftingRecipe#getRemainingItems} would work for plain shaped/shapeless
     * inners, but delegating is correct for any inner (custom crafting recipes with
     * their own remainder logic) and keeps the wrapper fully transparent. This is
     * the value {@code ResultSlot.onTake} consumes once the recipe resolves — see
     * {@link com.cyberday1.neoorigins.mixin.recipe.ResultSlotOriginContextMixin} for
     * why the gate must be planted so this override is reached instead of vanilla's
     * "copy the whole grid back" fallback.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return inner.getRemainingItems(input);
    }

    @Override
    public CraftingBookCategory category() {
        return inner.category();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return OriginRecipeRegistry.ORIGIN_GATED_CRAFTING_SERIALIZER.get();
    }

    @Override
    public boolean isSpecial() {
        // Delegate to the inner recipe (false for normal shaped/shapeless). A
        // "special" recipe is excluded from the recipe book entirely AND skipped
        // by ServerRecipeBook.addRecipes — so marking this special silently
        // defeated parseRecipe's player.awardRecipes() call: gated recipes could
        // never be unlocked or shown in the book. The per-player gate is still
        // enforced at craft time by matches(); the book may show the recipe as
        // a non-highlighted entry for players who can't yet satisfy the gate,
        // which is the correct, discoverable behaviour.
        return inner.isSpecial();
    }

    public static ResourceLocation typeId() {
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "origin_gated_crafting");
    }
}
