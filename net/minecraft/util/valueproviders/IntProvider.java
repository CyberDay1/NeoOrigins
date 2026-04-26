package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

public abstract class IntProvider {
    private static final Codec<Either<Integer, IntProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(
        Codec.INT, BuiltInRegistries.INT_PROVIDER_TYPE.byNameCodec().dispatch(IntProvider::getType, IntProviderType::codec)
    );
    public static final Codec<IntProvider> CODEC = CONSTANT_OR_DISPATCH_CODEC.xmap(
        p_146543_ -> p_146543_.map(ConstantInt::of, p_146549_ -> (IntProvider)p_146549_),
        p_146541_ -> p_146541_.getType() == IntProviderType.CONSTANT ? Either.left(((ConstantInt)p_146541_).getValue()) : Either.right(p_146541_)
    );
    public static final Codec<IntProvider> NON_NEGATIVE_CODEC = codec(0, Integer.MAX_VALUE);
    public static final Codec<IntProvider> POSITIVE_CODEC = codec(1, Integer.MAX_VALUE);

    public static Codec<IntProvider> codec(int p_146546_, int p_146547_) {
        return validateCodec(p_146546_, p_146547_, CODEC);
    }

    public static <T extends IntProvider> Codec<T> validateCodec(int p_338363_, int p_338219_, Codec<T> p_338307_) {
        return p_338307_.validate(p_337695_ -> validate(p_338363_, p_338219_, p_337695_));
    }

    private static <T extends IntProvider> DataResult<T> validate(int p_338299_, int p_338617_, T p_338788_) {
        if (p_338788_.getMinValue() < p_338299_) {
            return DataResult.error(() -> "Value provider too low: " + p_338299_ + " [" + p_338788_.getMinValue() + "-" + p_338788_.getMaxValue() + "]");
        } else {
            return p_338788_.getMaxValue() > p_338617_
                ? DataResult.error(() -> "Value provider too high: " + p_338617_ + " [" + p_338788_.getMinValue() + "-" + p_338788_.getMaxValue() + "]")
                : DataResult.success(p_338788_);
        }
    }

    public abstract int sample(RandomSource p_216855_);

    public abstract int getMinValue();

    public abstract int getMaxValue();

    public abstract IntProviderType<?> getType();
}
