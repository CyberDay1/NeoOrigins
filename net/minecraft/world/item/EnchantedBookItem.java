package net.minecraft.world.item;

import net.minecraft.world.item.enchantment.EnchantmentInstance;

public class EnchantedBookItem extends Item {
    public EnchantedBookItem(Item.Properties p_41149_) {
        super(p_41149_);
    }

    @Override
    public boolean isEnchantable(ItemStack p_41168_) {
        return false;
    }

    public static ItemStack createForEnchantment(EnchantmentInstance p_41162_) {
        ItemStack itemstack = new ItemStack(Items.ENCHANTED_BOOK);
        itemstack.enchant(p_41162_.enchantment, p_41162_.level);
        return itemstack;
    }
}
