package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record SimpleBlockConfiguration(BlockStateProvider toPlace) implements FeatureConfiguration {
    public static final Codec<SimpleBlockConfiguration> CODEC = RecordCodecBuilder.create(
        p_191331_ -> p_191331_.group(BlockStateProvider.CODEC.fieldOf("to_place").forGetter(p_161168_ -> p_161168_.toPlace))
                .apply(p_191331_, SimpleBlockConfiguration::new)
    );
}
