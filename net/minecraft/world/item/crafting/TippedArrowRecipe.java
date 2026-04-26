package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class TippedArrowRecipe extends CustomRecipe {
    public TippedArrowRecipe(CraftingBookCategory p_252163_) {
        super(p_252163_);
    }

    public boolean matches(CraftingInput p_345143_, Level p_44516_) {
        if (p_345143_.width() == 3 && p_345143_.height() == 3) {
            for (int i = 0; i < p_345143_.height(); i++) {
                for (int j = 0; j < p_345143_.width(); j++) {
                    ItemStack itemstack = p_345143_.getItem(j, i);
                    if (itemstack.isEmpty()) {
                        return false;
                    }

                    if (j == 1 && i == 1) {
                        if (!itemstack.is(Items.LINGERING_POTION)) {
                            return false;
                        }
                    } else if (!itemstack.is(Items.ARROW)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public ItemStack assemble(CraftingInput p_345174_, HolderLookup.Provider p_335792_) {
        ItemStack itemstack = p_345174_.getItem(1, 1);
        if (!itemstack.is(Items.LINGERING_POTION)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = new ItemStack(Items.TIPPED_ARROW, 8);
            itemstack1.set(DataComponents.POTION_CONTENTS, itemstack.get(DataComponents.POTION_CONTENTS));
            return itemstack1;
        }
    }

    @Override
    public boolean canCraftInDimensions(int p_44505_, int p_44506_) {
        return p_44505_ >= 3 && p_44506_ >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.TIPPED_ARROW;
    }
}
