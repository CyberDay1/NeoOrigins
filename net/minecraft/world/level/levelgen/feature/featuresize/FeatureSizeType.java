package net.minecraft.world.level.levelgen.feature.featuresize;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class FeatureSizeType<P extends FeatureSize> {
    public static final FeatureSizeType<TwoLayersFeatureSize> TWO_LAYERS_FEATURE_SIZE = register("two_layers_feature_size", TwoLayersFeatureSize.CODEC);
    public static final FeatureSizeType<ThreeLayersFeatureSize> THREE_LAYERS_FEATURE_SIZE = register("three_layers_feature_size", ThreeLayersFeatureSize.CODEC);
    private final MapCodec<P> codec;

    private static <P extends FeatureSize> FeatureSizeType<P> register(String p_68304_, MapCodec<P> p_338491_) {
        return Registry.register(BuiltInRegistries.FEATURE_SIZE_TYPE, p_68304_, new FeatureSizeType<>(p_338491_));
    }

    public FeatureSizeType(MapCodec<P> p_338873_) {
        this.codec = p_338873_;
    }

    public MapCodec<P> codec() {
        return this.codec;
    }
}
