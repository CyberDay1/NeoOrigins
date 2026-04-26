package net.minecraft.client.gui.screens.recipebook;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractFurnaceRecipeBookComponent extends RecipeBookComponent {
    private static final WidgetSprites FILTER_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_enabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_disabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_enabled_highlighted"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_disabled_highlighted")
    );
    @Nullable
    private Ingredient fuels;

    @Override
    protected void initFilterButtonTextures() {
        this.filterButton.initTextureValues(FILTER_SPRITES);
    }

    @Override
    public void slotClicked(@Nullable Slot p_100120_) {
        super.slotClicked(p_100120_);
        if (p_100120_ != null && p_100120_.index < this.menu.getSize()) {
            this.ghostRecipe.clear();
        }
    }

    @Override
    public void setupGhostRecipe(RecipeHolder<?> p_301099_, List<Slot> p_100123_) {
        ItemStack itemstack = p_301099_.value().getResultItem(this.minecraft.level.registryAccess());
        this.ghostRecipe.setRecipe(p_301099_);
        this.ghostRecipe.addIngredient(Ingredient.of(itemstack), p_100123_.get(2).x, p_100123_.get(2).y);
        NonNullList<Ingredient> nonnulllist = p_301099_.value().getIngredients();
        Slot slot = p_100123_.get(1);
        if (slot.getItem().isEmpty()) {
            if (this.fuels == null) {
                this.fuels = Ingredient.of(
                    this.getFuelItems().stream().filter(p_280880_ -> p_280880_.isEnabled(this.minecraft.level.enabledFeatures())).map(ItemStack::new)
                );
            }

            this.ghostRecipe.addIngredient(this.fuels, slot.x, slot.y);
        }

        Iterator<Ingredient> iterator = nonnulllist.iterator();

        for (int i = 0; i < 2; i++) {
            if (!iterator.hasNext()) {
                return;
            }

            Ingredient ingredient = iterator.next();
            if (!ingredient.isEmpty()) {
                Slot slot1 = p_100123_.get(i);
                this.ghostRecipe.addIngredient(ingredient, slot1.x, slot1.y);
            }
        }
    }

    protected abstract Set<Item> getFuelItems();
}
