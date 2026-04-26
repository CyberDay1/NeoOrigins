package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractBannerBlock extends BaseEntityBlock {
    private final DyeColor color;

    protected AbstractBannerBlock(DyeColor p_48659_, BlockBehaviour.Properties p_48660_) {
        super(p_48660_);
        this.color = p_48659_;
    }

    @Override
    protected abstract MapCodec<? extends AbstractBannerBlock> codec();

    @Override
    public boolean isPossibleToRespawnInThis(BlockState p_279267_) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_151892_, BlockState p_151893_) {
        return new BannerBlockEntity(p_151892_, p_151893_, this.color);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304796_, BlockPos p_48665_, BlockState p_48666_) {
        return p_304796_.getBlockEntity(p_48665_) instanceof BannerBlockEntity bannerblockentity
            ? bannerblockentity.getItem()
            : super.getCloneItemStack(p_304796_, p_48665_, p_48666_);
    }

    public DyeColor getColor() {
        return this.color;
    }
}
