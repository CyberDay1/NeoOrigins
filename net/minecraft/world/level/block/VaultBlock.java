package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public class VaultBlock extends BaseEntityBlock {
    public static final MapCodec<VaultBlock> CODEC = simpleCodec(VaultBlock::new);
    public static final Property<VaultState> STATE = BlockStateProperties.VAULT_STATE;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

    @Override
    public MapCodec<VaultBlock> codec() {
        return CODEC;
    }

    public VaultBlock(BlockBehaviour.Properties p_324605_) {
        super(p_324605_);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, VaultState.INACTIVE).setValue(OMINOUS, Boolean.valueOf(false))
        );
    }

    @Override
    public ItemInteractionResult useItemOn(
        ItemStack p_324161_, BlockState p_323816_, Level p_324403_, BlockPos p_324623_, Player p_324219_, InteractionHand p_324416_, BlockHitResult p_324261_
    ) {
        if (p_324161_.isEmpty() || p_323816_.getValue(STATE) != VaultState.ACTIVE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (p_324403_ instanceof ServerLevel serverlevel) {
            if (serverlevel.getBlockEntity(p_324623_) instanceof VaultBlockEntity vaultblockentity) {
                VaultBlockEntity.Server.tryInsertKey(
                    serverlevel,
                    p_324623_,
                    p_323816_,
                    vaultblockentity.getConfig(),
                    vaultblockentity.getServerData(),
                    vaultblockentity.getSharedData(),
                    p_324219_,
                    p_324161_
                );
                return ItemInteractionResult.SUCCESS;
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        } else {
            return ItemInteractionResult.CONSUME;
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_324543_, BlockState p_323652_) {
        return new VaultBlockEntity(p_324543_, p_323652_);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_323673_) {
        p_323673_.add(FACING, STATE, OMINOUS);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_323525_, BlockState p_324070_, BlockEntityType<T> p_323541_) {
        return p_323525_ instanceof ServerLevel serverlevel
            ? createTickerHelper(
                p_323541_,
                BlockEntityType.VAULT,
                (p_323957_, p_324322_, p_323828_, p_323769_) -> VaultBlockEntity.Server.tick(
                        serverlevel, p_324322_, p_323828_, p_323769_.getConfig(), p_323769_.getServerData(), p_323769_.getSharedData()
                    )
            )
            : createTickerHelper(
                p_323541_,
                BlockEntityType.VAULT,
                (p_324290_, p_323926_, p_323941_, p_323489_) -> VaultBlockEntity.Client.tick(
                        p_324290_, p_323926_, p_323941_, p_323489_.getClientData(), p_323489_.getSharedData()
                    )
            );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_324576_) {
        return this.defaultBlockState().setValue(FACING, p_324576_.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState p_324232_, Rotation p_324443_) {
        return p_324232_.setValue(FACING, p_324443_.rotate(p_324232_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState p_323894_, Mirror p_324242_) {
        return p_323894_.rotate(p_324242_.getRotation(p_323894_.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState p_324584_) {
        return RenderShape.MODEL;
    }
}
