package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenOriginScreenPayload(boolean isOrb, boolean forceReselect) implements CustomPacketPayload {

    public static final Type<OpenOriginScreenPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "open_origin_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenOriginScreenPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> { buf.writeBoolean(payload.isOrb()); buf.writeBoolean(payload.forceReselect()); },
            buf -> new OpenOriginScreenPayload(buf.readBoolean(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
