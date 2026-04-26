package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public class TippedArrowItem extends ArrowItem {
    public TippedArrowItem(Item.Properties p_43354_) {
        super(p_43354_);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack itemstack = super.getDefaultInstance();
        itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        return itemstack;
    }

    @Override
    public void appendHoverText(ItemStack p_43359_, Item.TooltipContext p_339681_, List<Component> p_43361_, TooltipFlag p_43362_) {
        PotionContents potioncontents = p_43359_.get(DataComponents.POTION_CONTENTS);
        if (potioncontents != null) {
            potioncontents.addPotionTooltip(p_43361_::add, 0.125F, p_339681_.tickRate());
        }
    }

    @Override
    public String getDescriptionId(ItemStack p_43364_) {
        return Potion.getName(p_43364_.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion(), this.getDescriptionId() + ".effect.");
    }
}
