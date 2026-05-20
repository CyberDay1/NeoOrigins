package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S: ask the server to give the player a spawn egg pre-tagged with a
 * specific mob origin. Empty {@code entityTypeOverride} means "use the
 * origin's single {@code target.entity_type}"; non-empty overrides for
 * tag- or list-targeted origins.
 */
public record RequestMobOriginEggPayload(
    String originId,
    String entityTypeOverride,
    int count
) implements CustomPacketPayload {

    public static final Type<RequestMobOriginEggPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "request_mob_origin_egg"));

    public static final StreamCodec<FriendlyByteBuf, RequestMobOriginEggPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.originId, 256);
                buf.writeUtf(p.entityTypeOverride, 256);
                buf.writeVarInt(p.count);
            },
            buf -> new RequestMobOriginEggPayload(
                buf.readUtf(256), buf.readUtf(256), buf.readVarInt())
        );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
