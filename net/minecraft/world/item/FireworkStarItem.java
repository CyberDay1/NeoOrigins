package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.FireworkExplosion;

public class FireworkStarItem extends Item {
    public FireworkStarItem(Item.Properties p_41248_) {
        super(p_41248_);
    }

    @Override
    public void appendHoverText(ItemStack p_41252_, Item.TooltipContext p_339685_, List<Component> p_41254_, TooltipFlag p_41255_) {
        FireworkExplosion fireworkexplosion = p_41252_.get(DataComponents.FIREWORK_EXPLOSION);
        if (fireworkexplosion != null) {
            fireworkexplosion.addToTooltip(p_339685_, p_41254_::add, p_41255_);
        }
    }
}
