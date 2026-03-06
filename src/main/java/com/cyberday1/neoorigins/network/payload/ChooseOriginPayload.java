package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChooseOriginPayload(
    Identifier layer,
    Identifier origin
) implements CustomPacketPayload {

    public static final Type<ChooseOriginPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "choose_origin"));

    public static final StreamCodec<FriendlyByteBuf, ChooseOriginPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeIdentifier(payload.layer());
                buf.writeIdentifier(payload.origin());
            },
            buf -> new ChooseOriginPayload(buf.readIdentifier(), buf.readIdentifier())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
