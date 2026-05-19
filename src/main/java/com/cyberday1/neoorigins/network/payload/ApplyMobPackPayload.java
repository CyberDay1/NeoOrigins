package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C→S: trigger the shared datapack reload after a mob-origin Save. */
public record ApplyMobPackPayload() implements CustomPacketPayload {
    public static final Type<ApplyMobPackPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "apply_mob_pack"));
    public static final StreamCodec<FriendlyByteBuf, ApplyMobPackPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new ApplyMobPackPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
