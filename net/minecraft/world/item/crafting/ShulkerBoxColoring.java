package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public class ShulkerBoxColoring extends CustomRecipe {
    public ShulkerBoxColoring(CraftingBookCategory p_250756_) {
        super(p_250756_);
    }

    public boolean matches(CraftingInput p_345624_, Level p_44325_) {
        int i = 0;
        int j = 0;

        for (int k = 0; k < p_345624_.size(); k++) {
            ItemStack itemstack = p_345624_.getItem(k);
            if (!itemstack.isEmpty()) {
                if (Block.byItem(itemstack.getItem()) instanceof ShulkerBoxBlock) {
                    i++;
                } else {
                    if (!itemstack.is(net.neoforged.neoforge.common.Tags.Items.DYES)) {
                        return false;
                    }

                    j++;
                }

                if (j > 1 || i > 1) {
                    return false;
                }
            }
        }

        return i == 1 && j == 1;
    }

    public ItemStack assemble(CraftingInput p_346433_, HolderLookup.Provider p_335717_) {
        ItemStack itemstack = ItemStack.EMPTY;
        net.minecraft.world.item.DyeColor dyecolor = net.minecraft.world.item.DyeColor.WHITE;

        for (int i = 0; i < p_346433_.size(); i++) {
            ItemStack itemstack1 = p_346433_.getItem(i);
            if (!itemstack1.isEmpty()) {
                Item item = itemstack1.getItem();
                if (Block.byItem(item) instanceof ShulkerBoxBlock) {
                    itemstack = itemstack1;
                } else {
                    net.minecraft.world.item.DyeColor tmp = net.minecraft.world.item.DyeColor.getColor(itemstack1);
                    if (tmp != null) dyecolor = tmp;
                }
            }
        }

        Block block = ShulkerBoxBlock.getBlockByColor(dyecolor);
        return itemstack.transmuteCopy(block, 1);
    }

    @Override
    public boolean canCraftInDimensions(int p_44314_, int p_44315_) {
        return p_44314_ * p_44315_ >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHULKER_BOX_COLORING;
    }
}
