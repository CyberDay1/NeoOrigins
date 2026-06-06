package com.cyberday1.neoorigins.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Duck-type interface mixed onto {@link net.minecraft.world.item.crafting.RecipeManager}
 * by {@code mixin.recipe.RecipeManagerReplaceMixin} on 26.1.
 *
 * <p>26.1 dropped the public {@code RecipeManager.replaceRecipes(Iterable)} hook
 * that 1.21.1 exposes (recipes now live in an otherwise-immutable
 * {@link net.minecraft.world.item.crafting.RecipeMap}). This interface restores
 * the same capability so {@link com.cyberday1.neoorigins.service.InlineRecipeRegistry}
 * can inject inline {@code origins:recipe} recipes and wrap referenced recipes in
 * an {@link OriginGatedRecipe} after each datapack reload, exactly as on 1.21.1.
 *
 * <p>The live {@code RecipeManager} instance always implements this interface at
 * runtime once the mixin applies, so callers cast it directly:
 * {@code ((RecipeManagerReplaceAccess) server.getRecipeManager()).neoorigins$replaceRecipes(...)}.
 */
public interface RecipeManagerReplaceAccess {

    /**
     * Rebuild the manager's recipe set from {@code recipes} (replacing the
     * current {@link net.minecraft.world.item.crafting.RecipeMap}) and re-run
     * {@code finalizeRecipeLoading} so the derived display / property-set state
     * stays consistent. Mirrors 1.21.1's {@code RecipeManager.replaceRecipes}.
     */
    void neoorigins$replaceRecipes(Iterable<RecipeHolder<?>> recipes);
}
