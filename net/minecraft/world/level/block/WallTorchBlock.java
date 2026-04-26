package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallTorchBlock extends TorchBlock {
    public static final MapCodec<WallTorchBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308849_ -> p_308849_.group(PARTICLE_OPTIONS_FIELD.forGetter(p_304470_ -> p_304470_.flameParticle), propertiesCodec())
                .apply(p_308849_, WallTorchBlock::new)
    );
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    protected static final float AABB_OFFSET = 2.5F;
    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(
        ImmutableMap.of(
            Direction.NORTH,
            Block.box(5.5, 3.0, 11.0, 10.5, 13.0, 16.0),
            Direction.SOUTH,
            Block.box(5.5, 3.0, 0.0, 10.5, 13.0, 5.0),
            Direction.WEST,
            Block.box(11.0, 3.0, 5.5, 16.0, 13.0, 10.5),
            Direction.EAST,
            Block.box(0.0, 3.0, 5.5, 5.0, 13.0, 10.5)
        )
    );

    @Override
    public MapCodec<WallTorchBlock> codec() {
        return CODEC;
    }

    public WallTorchBlock(SimpleParticleType p_304467_, BlockBehaviour.Properties p_58123_) {
        super(p_304467_, p_58123_);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public String getDescriptionId() {
        return this.asItem().getDescriptionId();
    }

    @Override
    protected VoxelShape getShape(BlockState p_58152_, BlockGetter p_58153_, BlockPos p_58154_, CollisionContext p_58155_) {
        return getShape(p_58152_);
    }

    public static VoxelShape getShape(BlockState p_58157_) {
        return AABBS.get(p_58157_.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState p_58133_, LevelReader p_58134_, BlockPos p_58135_) {
        return canSurvive(p_58134_, p_58135_, p_58133_.getValue(FACING));
    }

    public static boolean canSurvive(LevelReader p_320074_, BlockPos p_320860_, Direction p_320326_) {
        BlockPos blockpos = p_320860_.relative(p_320326_.getOpposite());
        BlockState blockstate = p_320074_.getBlockState(blockpos);
        return blockstate.isFaceSturdy(p_320074_, blockpos, p_320326_);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_58126_) {
        BlockState blockstate = this.defaultBlockState();
        LevelReader levelreader = p_58126_.getLevel();
        BlockPos blockpos = p_58126_.getClickedPos();
        Direction[] adirection = p_58126_.getNearestLookingDirections();

        for (Direction direction : adirection) {
            if (direction.getAxis().isHorizontal()) {
                Direction direction1 = direction.getOpposite();
                blockstate = blockstate.setValue(FACING, direction1);
                if (blockstate.canSurvive(levelreader, blockpos)) {
                    return blockstate;
                }
            }
        }

        return null;
    }

    @Override
    protected BlockState updateShape(BlockState p_58143_, Direction p_58144_, BlockState p_58145_, LevelAccessor p_58146_, BlockPos p_58147_, BlockPos p_58148_) {
        return p_58144_.getOpposite() == p_58143_.getValue(FACING) && !p_58143_.canSurvive(p_58146_, p_58147_) ? Blocks.AIR.defaultBlockState() : p_58143_;
    }

    @Override
    public void animateTick(BlockState p_222660_, Level p_222661_, BlockPos p_222662_, RandomSource p_222663_) {
        Direction direction = p_222660_.getValue(FACING);
        double d0 = (double)p_222662_.getX() + 0.5;
        double d1 = (double)p_222662_.getY() + 0.7;
        double d2 = (double)p_222662_.getZ() + 0.5;
        double d3 = 0.22;
        double d4 = 0.27;
        Direction direction1 = direction.getOpposite();
        p_222661_.addParticle(
            ParticleTypes.SMOKE, d0 + 0.27 * (double)direction1.getStepX(), d1 + 0.22, d2 + 0.27 * (double)direction1.getStepZ(), 0.0, 0.0, 0.0
        );
        p_222661_.addParticle(
            this.flameParticle, d0 + 0.27 * (double)direction1.getStepX(), d1 + 0.22, d2 + 0.27 * (double)direction1.getStepZ(), 0.0, 0.0, 0.0
        );
    }

    @Override
    protected BlockState rotate(BlockState p_58140_, Rotation p_58141_) {
        return p_58140_.setValue(FACING, p_58141_.rotate(p_58140_.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState p_58137_, Mirror p_58138_) {
        return p_58137_.rotate(p_58138_.getRotation(p_58137_.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_58150_) {
        p_58150_.add(FACING);
    }
}
