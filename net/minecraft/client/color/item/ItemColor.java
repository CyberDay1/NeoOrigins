package net.minecraft.client.color.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ItemColor {
    int getColor(ItemStack p_92672_, int p_92673_);
}
