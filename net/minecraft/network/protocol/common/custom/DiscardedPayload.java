package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record DiscardedPayload(ResourceLocation id) implements CustomPacketPayload {
    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(ResourceLocation p_320106_, int p_319929_) {
        return CustomPacketPayload.codec((p_320462_, p_319882_) -> {
        }, p_319935_ -> {
            int i = p_319935_.readableBytes();
            if (i >= 0 && i <= p_319929_) {
                p_319935_.skipBytes(i);
                return new DiscardedPayload(p_320106_);
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + p_319929_ + " bytes");
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DiscardedPayload> type() {
        return new CustomPacketPayload.Type<>(this.id);
    }
}
