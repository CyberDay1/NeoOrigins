package net.minecraft.world.item.component;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public interface TooltipProvider {
    void addToTooltip(Item.TooltipContext p_340892_, Consumer<Component> p_330337_, TooltipFlag p_331069_);
}
