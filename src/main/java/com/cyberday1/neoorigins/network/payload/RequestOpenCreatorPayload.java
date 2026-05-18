package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client→server (keybind / menu) to request opening the 2.1 creator. The
 * server checks {@code CreatorAccess} before replying with
 * {@link OpenEditorScreenPayload}; it is never opened client-side directly.
 */
public record RequestOpenCreatorPayload() implements CustomPacketPayload {

    public static final Type<RequestOpenCreatorPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "request_open_creator"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenCreatorPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new RequestOpenCreatorPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
