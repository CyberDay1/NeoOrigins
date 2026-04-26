package net.minecraft.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class BlastingRecipe extends AbstractCookingRecipe {
    public BlastingRecipe(String p_251053_, CookingBookCategory p_249936_, Ingredient p_251550_, ItemStack p_251027_, float p_250843_, int p_249841_) {
        super(RecipeType.BLASTING, p_251053_, p_249936_, p_251550_, p_251027_, p_250843_, p_249841_);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Blocks.BLAST_FURNACE);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.BLASTING_RECIPE;
    }
}
