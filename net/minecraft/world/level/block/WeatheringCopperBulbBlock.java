package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WeatheringCopperBulbBlock extends CopperBulbBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperBulbBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_309135_ -> p_309135_.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperBulbBlock::getAge), propertiesCodec()
                )
                .apply(p_309135_, WeatheringCopperBulbBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    protected MapCodec<WeatheringCopperBulbBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperBulbBlock(WeatheringCopper.WeatherState p_308927_, BlockBehaviour.Properties p_309010_) {
        super(p_309010_);
        this.weatherState = p_308927_;
    }

    @Override
    protected void randomTick(BlockState p_309163_, ServerLevel p_309177_, BlockPos p_309033_, RandomSource p_308946_) {
        this.changeOverTime(p_309163_, p_309177_, p_309033_, p_308946_);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState p_308966_) {
        return WeatheringCopper.getNext(p_308966_.getBlock()).isPresent();
    }

    public WeatheringCopper.WeatherState getAge() {
        return this.weatherState;
    }
}
