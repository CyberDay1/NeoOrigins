package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenOriginScreenPayload(boolean isOrb) implements CustomPacketPayload {

    public static final Type<OpenOriginScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "open_origin_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenOriginScreenPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.isOrb()),
            buf -> new OpenOriginScreenPayload(buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
