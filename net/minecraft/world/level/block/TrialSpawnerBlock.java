package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class TrialSpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<TrialSpawnerBlock> CODEC = simpleCodec(TrialSpawnerBlock::new);
    public static final EnumProperty<TrialSpawnerState> STATE = BlockStateProperties.TRIAL_SPAWNER_STATE;
    public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

    @Override
    public MapCodec<TrialSpawnerBlock> codec() {
        return CODEC;
    }

    public TrialSpawnerBlock(BlockBehaviour.Properties p_312795_) {
        super(p_312795_);
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, TrialSpawnerState.INACTIVE).setValue(OMINOUS, Boolean.valueOf(false)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_312785_) {
        p_312785_.add(STATE, OMINOUS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_312710_) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_311941_, BlockState p_312821_) {
        return new TrialSpawnerBlockEntity(p_311941_, p_312821_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_311756_, BlockState p_312797_, BlockEntityType<T> p_312122_) {
        return p_311756_ instanceof ServerLevel serverlevel
            ? createTickerHelper(
                p_312122_,
                BlockEntityType.TRIAL_SPAWNER,
                (p_337976_, p_337977_, p_337978_, p_337979_) -> p_337979_.getTrialSpawner()
                        .tickServer(serverlevel, p_337977_, p_337978_.getOptionalValue(BlockStateProperties.OMINOUS).orElse(false))
            )
            : createTickerHelper(
                p_312122_,
                BlockEntityType.TRIAL_SPAWNER,
                (p_337980_, p_337981_, p_337982_, p_337983_) -> p_337983_.getTrialSpawner()
                        .tickClient(p_337980_, p_337981_, p_337982_.getOptionalValue(BlockStateProperties.OMINOUS).orElse(false))
            );
    }

    @Override
    public void appendHoverText(ItemStack p_312446_, Item.TooltipContext p_339621_, List<Component> p_312088_, TooltipFlag p_311895_) {
        super.appendHoverText(p_312446_, p_339621_, p_312088_, p_311895_);
        Spawner.appendHoverText(p_312446_, p_312088_, "spawn_data");
    }
}
