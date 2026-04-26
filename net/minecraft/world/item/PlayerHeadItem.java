package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public class PlayerHeadItem extends StandingAndWallBlockItem {
    public PlayerHeadItem(Block p_42971_, Block p_42972_, Item.Properties p_42973_) {
        super(p_42971_, p_42972_, p_42973_, Direction.DOWN);
    }

    @Override
    public Component getName(ItemStack p_42977_) {
        ResolvableProfile resolvableprofile = p_42977_.get(DataComponents.PROFILE);
        return (Component)(resolvableprofile != null && resolvableprofile.name().isPresent()
            ? Component.translatable(this.getDescriptionId() + ".named", resolvableprofile.name().get())
            : super.getName(p_42977_));
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack p_331032_) {
        ResolvableProfile resolvableprofile = p_331032_.get(DataComponents.PROFILE);
        if (resolvableprofile != null && !resolvableprofile.isResolved()) {
            resolvableprofile.resolve()
                .thenAcceptAsync(p_332155_ -> p_331032_.set(DataComponents.PROFILE, p_332155_), SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        }
    }
}
