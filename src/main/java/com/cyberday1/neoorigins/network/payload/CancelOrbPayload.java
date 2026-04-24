package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the origin picker just closed without the player committing
 * any layer. Lets the server clear {@code pendingOrbCommit} so a later pick via
 * {@code /origin gui} doesn't accidentally consume a still-undecided orb use.
 */
public record CancelOrbPayload() implements CustomPacketPayload {

    public static final Type<CancelOrbPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "cancel_orb"));

    public static final StreamCodec<FriendlyByteBuf, CancelOrbPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new CancelOrbPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
