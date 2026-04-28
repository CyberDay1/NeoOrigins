package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncMoisturePayload(float moisture) implements CustomPacketPayload {

    public static final Type<SyncMoisturePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_moisture"));

    public static final StreamCodec<FriendlyByteBuf, SyncMoisturePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.moisture()),
            buf -> new SyncMoisturePayload(buf.readFloat())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
