package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChooseOriginPayload(
    ResourceLocation layer,
    ResourceLocation origin
) implements CustomPacketPayload {

    public static final Type<ChooseOriginPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "choose_origin"));

    public static final StreamCodec<FriendlyByteBuf, ChooseOriginPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeResourceLocation(payload.layer());
                buf.writeResourceLocation(payload.origin());
            },
            buf -> new ChooseOriginPayload(buf.readResourceLocation(), buf.readResourceLocation())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
