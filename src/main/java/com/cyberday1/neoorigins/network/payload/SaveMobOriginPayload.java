package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C→S: the serialized MobOriginDraft (validated + written server-side). */
public record SaveMobOriginPayload(String draftJson) implements CustomPacketPayload {
    public static final Type<SaveMobOriginPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "save_mob_origin"));
    public static final StreamCodec<FriendlyByteBuf, SaveMobOriginPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.draftJson()),
            buf -> new SaveMobOriginPayload(buf.readUtf()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
