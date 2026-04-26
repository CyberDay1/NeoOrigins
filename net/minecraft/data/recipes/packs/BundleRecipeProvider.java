package net.minecraft.data.recipes.packs;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

public class BundleRecipeProvider extends RecipeProvider {
    public BundleRecipeProvider(PackOutput p_248813_, CompletableFuture<HolderLookup.Provider> p_323638_) {
        super(p_248813_, p_323638_);
    }

    @Override
    protected void buildRecipes(RecipeOutput p_301031_) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Items.BUNDLE)
            .define('#', Items.RABBIT_HIDE)
            .define('-', Items.STRING)
            .pattern("-#-")
            .pattern("# #")
            .pattern("###")
            .unlockedBy("has_string", has(Items.STRING))
            .save(p_301031_);
    }
}
