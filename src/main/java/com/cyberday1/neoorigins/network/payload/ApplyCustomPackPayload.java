package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client→server: the deliberate "Apply" action. After one or more saves,
 * this triggers {@code CustomPackReloadService} (gate-checked) so the world
 * datapack reload — and its visible hitch — happens when the author chooses.
 */
public record ApplyCustomPackPayload() implements CustomPacketPayload {

    public static final Type<ApplyCustomPackPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "apply_custom_pack"));

    public static final StreamCodec<FriendlyByteBuf, ApplyCustomPackPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new ApplyCustomPackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
