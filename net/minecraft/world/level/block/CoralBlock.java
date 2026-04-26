package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class CoralBlock extends Block {
    public static final MapCodec<Block> DEAD_CORAL_FIELD = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("dead");
    public static final MapCodec<CoralBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308817_ -> p_308817_.group(DEAD_CORAL_FIELD.forGetter(p_304453_ -> p_304453_.deadBlock), propertiesCodec()).apply(p_308817_, CoralBlock::new)
    );
    private final Block deadBlock;

    public CoralBlock(Block p_52130_, BlockBehaviour.Properties p_52131_) {
        super(p_52131_);
        this.deadBlock = p_52130_;
    }

    @Override
    public MapCodec<CoralBlock> codec() {
        return CODEC;
    }

    @Override
    protected void tick(BlockState p_221020_, ServerLevel p_221021_, BlockPos p_221022_, RandomSource p_221023_) {
        if (!this.scanForWater(p_221021_, p_221022_)) {
            p_221021_.setBlock(p_221022_, this.deadBlock.defaultBlockState(), 2);
        }
    }

    @Override
    protected BlockState updateShape(BlockState p_52143_, Direction p_52144_, BlockState p_52145_, LevelAccessor p_52146_, BlockPos p_52147_, BlockPos p_52148_) {
        if (!this.scanForWater(p_52146_, p_52147_)) {
            p_52146_.scheduleTick(p_52147_, this, 60 + p_52146_.getRandom().nextInt(40));
        }

        return super.updateShape(p_52143_, p_52144_, p_52145_, p_52146_, p_52147_, p_52148_);
    }

    protected boolean scanForWater(BlockGetter p_52135_, BlockPos p_52136_) {
        BlockState state = p_52135_.getBlockState(p_52136_);
        for (Direction direction : Direction.values()) {
            FluidState fluidstate = p_52135_.getFluidState(p_52136_.relative(direction));
            if (state.canBeHydrated(p_52135_, p_52136_, fluidstate, p_52136_.relative(direction))) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_52133_) {
        if (!this.scanForWater(p_52133_.getLevel(), p_52133_.getClickedPos())) {
            p_52133_.getLevel().scheduleTick(p_52133_.getClickedPos(), this, 60 + p_52133_.getLevel().getRandom().nextInt(40));
        }

        return this.defaultBlockState();
    }
}
