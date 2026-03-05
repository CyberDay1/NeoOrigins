package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActivatePowerPayload() implements CustomPacketPayload {

    public static final Type<ActivatePowerPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "activate_power"));

    public static final StreamCodec<FriendlyByteBuf, ActivatePowerPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new ActivatePowerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
