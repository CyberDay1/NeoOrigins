package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WeatheringCopperGrateBlock extends WaterloggedTransparentBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperGrateBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_309146_ -> p_309146_.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperGrateBlock::getAge), propertiesCodec()
                )
                .apply(p_309146_, WeatheringCopperGrateBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    protected MapCodec<WeatheringCopperGrateBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperGrateBlock(WeatheringCopper.WeatherState p_309130_, BlockBehaviour.Properties p_309077_) {
        super(p_309077_);
        this.weatherState = p_309130_;
    }

    @Override
    protected void randomTick(BlockState p_309111_, ServerLevel p_309121_, BlockPos p_309090_, RandomSource p_308865_) {
        this.changeOverTime(p_309111_, p_309121_, p_309090_, p_308865_);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState p_309102_) {
        return WeatheringCopper.getNext(p_309102_.getBlock()).isPresent();
    }

    public WeatheringCopper.WeatherState getAge() {
        return this.weatherState;
    }
}
