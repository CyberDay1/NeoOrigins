package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ColoredFallingBlock extends FallingBlock {
    public static final MapCodec<ColoredFallingBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308812_ -> p_308812_.group(ColorRGBA.CODEC.fieldOf("falling_dust_color").forGetter(p_304722_ -> p_304722_.dustColor), propertiesCodec())
                .apply(p_308812_, ColoredFallingBlock::new)
    );
    private final ColorRGBA dustColor;

    @Override
    public MapCodec<ColoredFallingBlock> codec() {
        return CODEC;
    }

    public ColoredFallingBlock(ColorRGBA p_304786_, BlockBehaviour.Properties p_304896_) {
        super(p_304896_);
        this.dustColor = p_304786_;
    }

    @Override
    public int getDustColor(BlockState p_304891_, BlockGetter p_304551_, BlockPos p_304702_) {
        return this.dustColor.rgba();
    }
}
