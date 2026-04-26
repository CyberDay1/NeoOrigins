package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EnchantingTableBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantingTableBlock> CODEC = simpleCodec(EnchantingTableBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
    public static final List<BlockPos> BOOKSHELF_OFFSETS = BlockPos.betweenClosedStream(-2, 0, -2, 2, 1, 2)
        .filter(p_341357_ -> Math.abs(p_341357_.getX()) == 2 || Math.abs(p_341357_.getZ()) == 2)
        .map(BlockPos::immutable)
        .toList();

    @Override
    public MapCodec<EnchantingTableBlock> codec() {
        return CODEC;
    }

    public EnchantingTableBlock(BlockBehaviour.Properties p_341305_) {
        super(p_341305_);
    }

    public static boolean isValidBookShelf(Level p_340976_, BlockPos p_340984_, BlockPos p_341294_) {
        return p_340976_.getBlockState(p_340984_.offset(p_341294_)).getEnchantPowerBonus(p_340976_, p_340984_.offset(p_341294_)) != 0
            && p_340976_.getBlockState(p_340984_.offset(p_341294_.getX() / 2, p_341294_.getY(), p_341294_.getZ() / 2))
                .is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState p_341280_) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState p_340823_, BlockGetter p_340890_, BlockPos p_340839_, CollisionContext p_341114_) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState p_341263_, Level p_341303_, BlockPos p_340983_, RandomSource p_341147_) {
        super.animateTick(p_341263_, p_341303_, p_340983_, p_341147_);

        for (BlockPos blockpos : BOOKSHELF_OFFSETS) {
            if (p_341147_.nextInt(16) == 0 && isValidBookShelf(p_341303_, p_340983_, blockpos)) {
                p_341303_.addParticle(
                    ParticleTypes.ENCHANT,
                    (double)p_340983_.getX() + 0.5,
                    (double)p_340983_.getY() + 2.0,
                    (double)p_340983_.getZ() + 0.5,
                    (double)((float)blockpos.getX() + p_341147_.nextFloat()) - 0.5,
                    (double)((float)blockpos.getY() - p_341147_.nextFloat() - 1.0F),
                    (double)((float)blockpos.getZ() + p_341147_.nextFloat()) - 0.5
                );
            }
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_340914_) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_341190_, BlockState p_340989_) {
        return new EnchantingTableBlockEntity(p_341190_, p_340989_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_340880_, BlockState p_341416_, BlockEntityType<T> p_341078_) {
        return p_340880_.isClientSide ? createTickerHelper(p_341078_, BlockEntityType.ENCHANTING_TABLE, EnchantingTableBlockEntity::bookAnimationTick) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_341077_, Level p_341293_, BlockPos p_341394_, Player p_340990_, BlockHitResult p_341300_) {
        if (p_341293_.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            p_340990_.openMenu(p_341077_.getMenuProvider(p_341293_, p_341394_));
            return InteractionResult.CONSUME;
        }
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState p_341244_, Level p_340950_, BlockPos p_340923_) {
        BlockEntity blockentity = p_340950_.getBlockEntity(p_340923_);
        if (blockentity instanceof EnchantingTableBlockEntity) {
            Component component = ((Nameable)blockentity).getDisplayName();
            return new SimpleMenuProvider(
                (p_341299_, p_341308_, p_341334_) -> new EnchantmentMenu(p_341299_, p_341308_, ContainerLevelAccess.create(p_340950_, p_340923_)), component
            );
        } else {
            return null;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState p_341007_, PathComputationType p_341167_) {
        return false;
    }
}
