package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;

public class SuspiciousStewRecipe extends CustomRecipe {
    public SuspiciousStewRecipe(CraftingBookCategory p_250392_) {
        super(p_250392_);
    }

    public boolean matches(CraftingInput p_345878_, Level p_44500_) {
        boolean flag = false;
        boolean flag1 = false;
        boolean flag2 = false;
        boolean flag3 = false;

        for (int i = 0; i < p_345878_.size(); i++) {
            ItemStack itemstack = p_345878_.getItem(i);
            if (!itemstack.isEmpty()) {
                if (itemstack.is(Blocks.BROWN_MUSHROOM.asItem()) && !flag2) {
                    flag2 = true;
                } else if (itemstack.is(Blocks.RED_MUSHROOM.asItem()) && !flag1) {
                    flag1 = true;
                } else if (itemstack.is(ItemTags.SMALL_FLOWERS) && !flag) {
                    flag = true;
                } else {
                    if (!itemstack.is(Items.BOWL) || flag3) {
                        return false;
                    }

                    flag3 = true;
                }
            }
        }

        return flag && flag2 && flag1 && flag3;
    }

    public ItemStack assemble(CraftingInput p_346220_, HolderLookup.Provider p_336034_) {
        ItemStack itemstack = new ItemStack(Items.SUSPICIOUS_STEW, 1);

        for (int i = 0; i < p_346220_.size(); i++) {
            ItemStack itemstack1 = p_346220_.getItem(i);
            if (!itemstack1.isEmpty()) {
                SuspiciousEffectHolder suspiciouseffectholder = SuspiciousEffectHolder.tryGet(itemstack1.getItem());
                if (suspiciouseffectholder != null) {
                    itemstack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, suspiciouseffectholder.getSuspiciousEffects());
                    break;
                }
            }
        }

        return itemstack;
    }

    @Override
    public boolean canCraftInDimensions(int p_44489_, int p_44490_) {
        return p_44489_ >= 2 && p_44490_ >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SUSPICIOUS_STEW;
    }
}
