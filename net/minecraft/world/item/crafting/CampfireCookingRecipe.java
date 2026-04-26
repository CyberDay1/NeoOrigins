package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class CampfireCookingRecipe extends AbstractCookingRecipe {
    public CampfireCookingRecipe(String p_250140_, CookingBookCategory p_251808_, Ingredient p_249826_, ItemStack p_251839_, float p_251432_, int p_251471_) {
        super(RecipeType.CAMPFIRE_COOKING, p_250140_, p_251808_, p_249826_, p_251839_, p_251432_, p_251471_);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Blocks.CAMPFIRE);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
    }
}
