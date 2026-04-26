package net.minecraft.world.inventory;

import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public abstract class RecipeBookMenu<I extends RecipeInput, R extends Recipe<I>> extends AbstractContainerMenu {
    public RecipeBookMenu(MenuType<?> p_40115_, int p_40116_) {
        super(p_40115_, p_40116_);
    }

    public void handlePlacement(boolean p_40119_, RecipeHolder<?> p_300860_, ServerPlayer p_40121_) {
        RecipeHolder<R> recipeholder = (RecipeHolder<R>)p_300860_;
        this.beginPlacingRecipe();

        try {
            new ServerPlaceRecipe<>(this).recipeClicked(p_40121_, recipeholder, p_40119_);
        } finally {
            this.finishPlacingRecipe((RecipeHolder<R>)p_300860_);
        }
    }

    protected void beginPlacingRecipe() {
    }

    protected void finishPlacingRecipe(RecipeHolder<R> p_345813_) {
    }

    public abstract void fillCraftSlotsStackedContents(StackedContents p_40117_);

    public abstract void clearCraftingContent();

    public abstract boolean recipeMatches(RecipeHolder<R> p_301144_);

    public abstract int getResultSlotIndex();

    public abstract int getGridWidth();

    public abstract int getGridHeight();

    public abstract int getSize();

    public java.util.List<net.minecraft.client.RecipeBookCategories> getRecipeBookCategories() {
         return net.minecraft.client.RecipeBookCategories.getCategories(this.getRecipeBookType());
    }

    public abstract RecipeBookType getRecipeBookType();

    public abstract boolean shouldMoveToInventory(int p_150635_);
}
