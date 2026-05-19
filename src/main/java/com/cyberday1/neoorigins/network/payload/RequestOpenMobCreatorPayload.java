package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C→S: request to open the Mob Origin Creator (server gates + opens). */
public record RequestOpenMobCreatorPayload() implements CustomPacketPayload {
    public static final Type<RequestOpenMobCreatorPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "request_open_mob_creator"));
    public static final StreamCodec<FriendlyByteBuf, RequestOpenMobCreatorPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new RequestOpenMobCreatorPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
