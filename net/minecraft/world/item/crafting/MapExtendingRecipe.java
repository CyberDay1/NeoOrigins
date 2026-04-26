package net.minecraft.world.item.crafting;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapExtendingRecipe extends ShapedRecipe {
    public MapExtendingRecipe(CraftingBookCategory p_250154_) {
        super(
            "",
            p_250154_,
            ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.PAPER), 'x', Ingredient.of(Items.FILLED_MAP)), "###", "#x#", "###"),
            new ItemStack(Items.MAP)
        );
    }

    @Override
    public boolean matches(CraftingInput p_345101_, Level p_43994_) {
        if (!super.matches(p_345101_, p_43994_)) {
            return false;
        } else {
            ItemStack itemstack = findFilledMap(p_345101_);
            if (itemstack.isEmpty()) {
                return false;
            } else {
                MapItemSavedData mapitemsaveddata = MapItem.getSavedData(itemstack, p_43994_);
                if (mapitemsaveddata == null) {
                    return false;
                } else {
                    return mapitemsaveddata.isExplorationMap() ? false : mapitemsaveddata.scale < 4;
                }
            }
        }
    }

    @Override
    public ItemStack assemble(CraftingInput p_345831_, HolderLookup.Provider p_335387_) {
        ItemStack itemstack = findFilledMap(p_345831_).copyWithCount(1);
        itemstack.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        return itemstack;
    }

    private static ItemStack findFilledMap(CraftingInput p_344732_) {
        for (int i = 0; i < p_344732_.size(); i++) {
            ItemStack itemstack = p_344732_.getItem(i);
            if (itemstack.is(Items.FILLED_MAP)) {
                return itemstack;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.MAP_EXTENDING;
    }
}
