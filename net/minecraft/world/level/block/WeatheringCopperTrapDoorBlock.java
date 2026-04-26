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

public class WeatheringCopperTrapDoorBlock extends TrapDoorBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperTrapDoorBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308882_ -> p_308882_.group(
                    BlockSetType.CODEC.fieldOf("block_set_type").forGetter(TrapDoorBlock::getType),
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperTrapDoorBlock::getAge),
                    propertiesCodec()
                )
                .apply(p_308882_, WeatheringCopperTrapDoorBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    public MapCodec<WeatheringCopperTrapDoorBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperTrapDoorBlock(BlockSetType p_309013_, WeatheringCopper.WeatherState p_309166_, BlockBehaviour.Properties p_308943_) {
        super(p_309013_, p_308943_);
        this.weatherState = p_309166_;
    }

    @Override
    protected void randomTick(BlockState p_309138_, ServerLevel p_309005_, BlockPos p_309123_, RandomSource p_309094_) {
        this.changeOverTime(p_309138_, p_309005_, p_309123_, p_309094_);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState p_309078_) {
        return WeatheringCopper.getNext(p_309078_.getBlock()).isPresent();
    }

    public WeatheringCopper.WeatherState getAge() {
        return this.weatherState;
    }
}
