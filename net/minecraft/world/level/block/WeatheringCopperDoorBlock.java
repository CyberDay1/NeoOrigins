package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class WeatheringCopperDoorBlock extends DoorBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperDoorBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_309083_ -> p_309083_.group(
                    BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::type),
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperDoorBlock::getAge),
                    propertiesCodec()
                )
                .apply(p_309083_, WeatheringCopperDoorBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    public MapCodec<WeatheringCopperDoorBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperDoorBlock(BlockSetType p_309051_, WeatheringCopper.WeatherState p_308937_, BlockBehaviour.Properties p_309122_) {
        super(p_309051_, p_309122_);
        this.weatherState = p_308937_;
    }

    @Override
    protected void randomTick(BlockState p_308942_, ServerLevel p_309109_, BlockPos p_309034_, RandomSource p_309156_) {
        if (p_308942_.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            this.changeOverTime(p_308942_, p_309109_, p_309034_, p_309156_);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState p_308997_) {
        return WeatheringCopper.getNext(p_308997_.getBlock()).isPresent();
    }

    public WeatheringCopper.WeatherState getAge() {
        return this.weatherState;
    }
}
