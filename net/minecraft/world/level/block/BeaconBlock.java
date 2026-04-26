package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BeaconBlock extends BaseEntityBlock implements BeaconBeamBlock {
    public static final MapCodec<BeaconBlock> CODEC = simpleCodec(BeaconBlock::new);

    @Override
    public MapCodec<BeaconBlock> codec() {
        return CODEC;
    }

    public BeaconBlock(BlockBehaviour.Properties p_49421_) {
        super(p_49421_);
    }

    @Override
    public DyeColor getColor() {
        return DyeColor.WHITE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_152164_, BlockState p_152165_) {
        return new BeaconBlockEntity(p_152164_, p_152165_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_152160_, BlockState p_152161_, BlockEntityType<T> p_152162_) {
        return createTickerHelper(p_152162_, BlockEntityType.BEACON, BeaconBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_49432_, Level p_49433_, BlockPos p_49434_, Player p_49435_, BlockHitResult p_49437_) {
        if (p_49433_.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (p_49433_.getBlockEntity(p_49434_) instanceof BeaconBlockEntity beaconblockentity) {
                p_49435_.openMenu(beaconblockentity);
                p_49435_.awardStat(Stats.INTERACT_WITH_BEACON);
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_49439_) {
        return RenderShape.MODEL;
    }
}
