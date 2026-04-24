package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the origin picker just closed and the player committed
 * zero origins. Tells the server to drop first-pick invulnerability so the
 * player can't stay immortal forever by escaping the picker.
 */
public record PickerAbandonedPayload() implements CustomPacketPayload {

    public static final Type<PickerAbandonedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "picker_abandoned"));

    public static final StreamCodec<FriendlyByteBuf, PickerAbandonedPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new PickerAbandonedPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
