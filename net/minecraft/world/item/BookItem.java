package net.minecraft.world.item;

public class BookItem extends Item {
    public BookItem(Item.Properties p_40643_) {
        super(p_40643_);
    }

    @Override
    public boolean isEnchantable(ItemStack p_40646_) {
        return p_40646_.getCount() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
