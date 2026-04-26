package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public record PaintingVariant(int width, int height, ResourceLocation assetId) {
    public static final Codec<PaintingVariant> DIRECT_CODEC = RecordCodecBuilder.create(
        p_345420_ -> p_345420_.group(
                    ExtraCodecs.intRange(1, 16).fieldOf("width").forGetter(PaintingVariant::width),
                    ExtraCodecs.intRange(1, 16).fieldOf("height").forGetter(PaintingVariant::height),
                    ResourceLocation.CODEC.fieldOf("asset_id").forGetter(PaintingVariant::assetId)
                )
                .apply(p_345420_, PaintingVariant::new)
    );
    public static final StreamCodec<ByteBuf, PaintingVariant> DIRECT_STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        PaintingVariant::width,
        ByteBufCodecs.VAR_INT,
        PaintingVariant::height,
        ResourceLocation.STREAM_CODEC,
        PaintingVariant::assetId,
        PaintingVariant::new
    );
    public static final Codec<Holder<PaintingVariant>> CODEC = RegistryFileCodec.create(Registries.PAINTING_VARIANT, DIRECT_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<PaintingVariant>> STREAM_CODEC = ByteBufCodecs.holder(
        Registries.PAINTING_VARIANT, DIRECT_STREAM_CODEC
    );

    public int area() {
        return this.width() * this.height();
    }
}
