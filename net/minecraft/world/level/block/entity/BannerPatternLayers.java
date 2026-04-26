package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import org.slf4j.Logger;

public record BannerPatternLayers(List<BannerPatternLayers.Layer> layers) {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final BannerPatternLayers EMPTY = new BannerPatternLayers(List.of());
    public static final Codec<BannerPatternLayers> CODEC = BannerPatternLayers.Layer.CODEC.listOf().xmap(BannerPatternLayers::new, BannerPatternLayers::layers);
    public static final StreamCodec<RegistryFriendlyByteBuf, BannerPatternLayers> STREAM_CODEC = BannerPatternLayers.Layer.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(BannerPatternLayers::new, BannerPatternLayers::layers);

    public BannerPatternLayers removeLast() {
        return new BannerPatternLayers(List.copyOf(this.layers.subList(0, this.layers.size() - 1)));
    }

    public static class Builder {
        private final ImmutableList.Builder<BannerPatternLayers.Layer> layers = ImmutableList.builder();

        @Deprecated
        public BannerPatternLayers.Builder addIfRegistered(HolderGetter<BannerPattern> p_332806_, ResourceKey<BannerPattern> p_332663_, DyeColor p_332781_) {
            Optional<Holder.Reference<BannerPattern>> optional = p_332806_.get(p_332663_);
            if (optional.isEmpty()) {
                BannerPatternLayers.LOGGER.warn("Unable to find banner pattern with id: '{}'", p_332663_.location());
                return this;
            } else {
                return this.add(optional.get(), p_332781_);
            }
        }

        public BannerPatternLayers.Builder add(Holder<BannerPattern> p_330325_, DyeColor p_330891_) {
            return this.add(new BannerPatternLayers.Layer(p_330325_, p_330891_));
        }

        public BannerPatternLayers.Builder add(BannerPatternLayers.Layer p_331518_) {
            this.layers.add(p_331518_);
            return this;
        }

        public BannerPatternLayers.Builder addAll(BannerPatternLayers p_330600_) {
            this.layers.addAll(p_330600_.layers);
            return this;
        }

        public BannerPatternLayers build() {
            return new BannerPatternLayers(this.layers.build());
        }
    }

    public static record Layer(Holder<BannerPattern> pattern, DyeColor color) {
        public static final Codec<BannerPatternLayers.Layer> CODEC = RecordCodecBuilder.create(
            p_332633_ -> p_332633_.group(
                        BannerPattern.CODEC.fieldOf("pattern").forGetter(BannerPatternLayers.Layer::pattern),
                        DyeColor.CODEC.fieldOf("color").forGetter(BannerPatternLayers.Layer::color)
                    )
                    .apply(p_332633_, BannerPatternLayers.Layer::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, BannerPatternLayers.Layer> STREAM_CODEC = StreamCodec.composite(
            BannerPattern.STREAM_CODEC,
            BannerPatternLayers.Layer::pattern,
            DyeColor.STREAM_CODEC,
            BannerPatternLayers.Layer::color,
            BannerPatternLayers.Layer::new
        );

        public MutableComponent description() {
            String s = this.pattern.value().translationKey();
            return Component.translatable(s + "." + this.color.getName());
        }
    }
}
