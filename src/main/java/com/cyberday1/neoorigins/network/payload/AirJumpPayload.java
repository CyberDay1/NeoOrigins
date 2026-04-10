package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client->server when the player presses jump while airborne.
 * Used to trigger elytra-style flight for FlightPower.
 */
public record AirJumpPayload() implements CustomPacketPayload {

    public static final Type<AirJumpPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "air_jump"));

    public static final StreamCodec<FriendlyByteBuf, AirJumpPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {},
            buf -> new AirJumpPayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
