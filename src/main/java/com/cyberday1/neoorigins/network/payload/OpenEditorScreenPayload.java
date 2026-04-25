package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server→client to request that the client open the {@code OriginEditorScreen}.
 * Triggered by {@code /origin editor}.
 */
public record OpenEditorScreenPayload() implements CustomPacketPayload {

    public static final Type<OpenEditorScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "open_editor_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenEditorScreenPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new OpenEditorScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
