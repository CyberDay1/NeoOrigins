package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class AbstractCauldronBlock extends Block {
    private static final int SIDE_THICKNESS = 2;
    private static final int LEG_WIDTH = 4;
    private static final int LEG_HEIGHT = 3;
    private static final int LEG_DEPTH = 2;
    protected static final int FLOOR_LEVEL = 4;
    private static final VoxelShape INSIDE = box(2.0, 4.0, 2.0, 14.0, 16.0, 14.0);
    protected static final VoxelShape SHAPE = Shapes.join(
        Shapes.block(),
        Shapes.or(box(0.0, 0.0, 4.0, 16.0, 3.0, 12.0), box(4.0, 0.0, 0.0, 12.0, 3.0, 16.0), box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0), INSIDE),
        BooleanOp.ONLY_FIRST
    );
    protected final CauldronInteraction.InteractionMap interactions;

    @Override
    protected abstract MapCodec<? extends AbstractCauldronBlock> codec();

    public AbstractCauldronBlock(BlockBehaviour.Properties p_151946_, CauldronInteraction.InteractionMap p_304473_) {
        super(p_151946_);
        this.interactions = p_304473_;
    }

    protected double getContentHeight(BlockState p_151948_) {
        return 0.0;
    }

    protected boolean isEntityInsideContent(BlockState p_151980_, BlockPos p_151981_, Entity p_151982_) {
        return p_151982_.getY() < (double)p_151981_.getY() + this.getContentHeight(p_151980_)
            && p_151982_.getBoundingBox().maxY > (double)p_151981_.getY() + 0.25;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316791_, BlockState p_316287_, Level p_316530_, BlockPos p_316585_, Player p_316671_, InteractionHand p_316186_, BlockHitResult p_316294_
    ) {
        CauldronInteraction cauldroninteraction = this.interactions.map().get(p_316791_.getItem());
        return cauldroninteraction.interact(p_316287_, p_316530_, p_316585_, p_316671_, p_316186_, p_316791_);
    }

    @Override
    protected VoxelShape getShape(BlockState p_151964_, BlockGetter p_151965_, BlockPos p_151966_, CollisionContext p_151967_) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState p_151955_, BlockGetter p_151956_, BlockPos p_151957_) {
        return INSIDE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_151986_) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState p_151959_, PathComputationType p_151962_) {
        return false;
    }

    public abstract boolean isFull(BlockState p_151984_);

    @Override
    protected void tick(BlockState p_220702_, ServerLevel p_220703_, BlockPos p_220704_, RandomSource p_220705_) {
        BlockPos blockpos = PointedDripstoneBlock.findStalactiteTipAboveCauldron(p_220703_, p_220704_);
        if (blockpos != null) {
            Fluid fluid = PointedDripstoneBlock.getCauldronFillFluidType(p_220703_, blockpos);
            if (fluid != Fluids.EMPTY && this.canReceiveStalactiteDrip(fluid)) {
                this.receiveStalactiteDrip(p_220702_, p_220703_, p_220704_, fluid);
            }
        }
    }

    protected boolean canReceiveStalactiteDrip(Fluid p_151983_) {
        return false;
    }

    protected void receiveStalactiteDrip(BlockState p_151975_, Level p_151976_, BlockPos p_151977_, Fluid p_151978_) {
    }

    @Override
    public void onPlace(BlockState p_51978_, Level p_51979_, BlockPos p_51980_, BlockState p_51981_, boolean p_51982_) {
        super.onPlace(p_51978_, p_51979_, p_51980_, p_51981_, p_51982_);
        // Neo: Invalidate cauldron capabilities when a cauldron is added
        if (net.neoforged.neoforge.fluids.CauldronFluidContent.getForBlock(p_51981_.getBlock()) == null) {
            p_51979_.invalidateCapabilities(p_51980_);
        }
    }

    @Override
    public void onRemove(BlockState p_60515_, Level p_60516_, BlockPos p_60517_, BlockState p_60518_, boolean p_60519_) {
        super.onRemove(p_60515_, p_60516_, p_60517_, p_60518_, p_60519_);
        // Neo: Invalidate cauldron capabilities when a cauldron is removed
        if (net.neoforged.neoforge.fluids.CauldronFluidContent.getForBlock(p_60518_.getBlock()) == null) {
            p_60516_.invalidateCapabilities(p_60517_);
        }
    }
}
