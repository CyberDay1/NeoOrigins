package com.cyberday1.neoorigins.mixin.recipe;

import com.cyberday1.neoorigins.recipe.RecipeManagerReplaceAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores a {@code replaceRecipes}-style hook on 26.1's {@link RecipeManager}.
 *
 * <p>26.1 moved recipes into a {@link RecipeMap} whose {@code byKey} map is an
 * immutable {@code ImmutableMap}, and removed the public
 * {@code replaceRecipes(Iterable)} method 1.21.1 exposes. We shadow the private
 * {@code recipes} field and the public {@code finalizeRecipeLoading} method,
 * then rebuild the map from scratch via the public static
 * {@code RecipeMap.create(...)} factory and re-finalize so the derived state
 * ({@code allDisplays}, {@code recipeToDisplay}, {@code propertySets},
 * {@code stonecutterRecipes}) is rebuilt from the new recipe set.
 *
 * <p>The {@link FeatureFlagSet} needed by {@code finalizeRecipeLoading} isn't
 * available at the call site (it arrives during the reload), so we capture it
 * from the first {@code finalizeRecipeLoading} call into a {@link Unique} field.
 * By the time {@link com.cyberday1.neoorigins.service.InlineRecipeRegistry}
 * injects (from {@code OnDatapackSyncEvent}, which fires after the reload's
 * finalize pass), the flags are always captured. If for some reason they aren't,
 * we still swap the recipe map — craft-time {@code getRecipeFor} reads
 * {@code this.recipes} live, so gating still works; only the recipe-book display
 * cache would be momentarily stale.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerReplaceMixin implements RecipeManagerReplaceAccess {

    @Shadow private RecipeMap recipes;

    @Shadow public abstract void finalizeRecipeLoading(FeatureFlagSet enabledFlags);

    @Unique
    private FeatureFlagSet neoorigins$lastFlags;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void neoorigins$captureFlags(FeatureFlagSet enabledFlags, CallbackInfo ci) {
        this.neoorigins$lastFlags = enabledFlags;
    }

    @Override
    public void neoorigins$replaceRecipes(Iterable<RecipeHolder<?>> newRecipes) {
        this.recipes = RecipeMap.create(newRecipes);
        if (this.neoorigins$lastFlags != null) {
            this.finalizeRecipeLoading(this.neoorigins$lastFlags);
        }
    }
}
