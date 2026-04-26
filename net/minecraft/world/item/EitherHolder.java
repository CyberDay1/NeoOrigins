package net.minecraft.world.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record EitherHolder<T>(Optional<Holder<T>> holder, ResourceKey<T> key) {
    public EitherHolder(Holder<T> p_350710_) {
        this(Optional.of(p_350710_), p_350710_.unwrapKey().orElseThrow());
    }

    public EitherHolder(ResourceKey<T> p_350883_) {
        this(Optional.empty(), p_350883_);
    }

    public static <T> Codec<EitherHolder<T>> codec(ResourceKey<Registry<T>> p_350627_, Codec<Holder<T>> p_350442_) {
        return Codec.either(
                p_350442_,
                ResourceKey.codec(p_350627_).comapFlatMap(p_350331_ -> DataResult.error(() -> "Cannot parse as key without registry"), Function.identity())
            )
            .xmap(EitherHolder::fromEither, EitherHolder::asEither);
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, EitherHolder<T>> streamCodec(
        ResourceKey<Registry<T>> p_350628_, StreamCodec<RegistryFriendlyByteBuf, Holder<T>> p_350420_
    ) {
        return StreamCodec.composite(ByteBufCodecs.either(p_350420_, ResourceKey.streamCodec(p_350628_)), EitherHolder::asEither, EitherHolder::fromEither);
    }

    public Either<Holder<T>, ResourceKey<T>> asEither() {
        return this.holder.<Either<Holder<T>, ResourceKey<T>>>map(Either::left).orElseGet(() -> Either.right(this.key));
    }

    public static <T> EitherHolder<T> fromEither(Either<Holder<T>, ResourceKey<T>> p_350717_) {
        return p_350717_.map(EitherHolder::new, EitherHolder::new);
    }

    public Optional<T> unwrap(Registry<T> p_350328_) {
        return this.holder.map(Holder::value).or(() -> p_350328_.getOptional(this.key));
    }

    public Optional<Holder<T>> unwrap(HolderLookup.Provider p_350642_) {
        return this.holder.or(() -> p_350642_.lookupOrThrow(this.key.registryKey()).get(this.key));
    }
}
