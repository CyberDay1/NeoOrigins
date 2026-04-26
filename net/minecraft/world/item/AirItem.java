package net.minecraft.world.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class AirItem extends Item {
    private final Block block;

    public AirItem(Block p_40368_, Item.Properties p_40369_) {
        super(p_40369_);
        this.block = p_40368_;
    }

    @Override
    public String getDescriptionId() {
        return this.block.getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack p_40372_, Item.TooltipContext p_339659_, List<Component> p_40374_, TooltipFlag p_40375_) {
        super.appendHoverText(p_40372_, p_339659_, p_40374_, p_40375_);
        this.block.appendHoverText(p_40372_, p_339659_, p_40374_, p_40375_);
    }
}
