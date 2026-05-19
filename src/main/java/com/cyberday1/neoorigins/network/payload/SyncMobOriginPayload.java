package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * S→C: tells clients tracking entity {@code entityId} which mob origin it
 * carries (or none). Sent on spawn-time assignment and on clear. Phase 2
 * stores it in a client cache for future client-visible rendering; no
 * rendering consumes it yet.
 */
public record SyncMobOriginPayload(int entityId, Optional<ResourceLocation> originId)
        implements CustomPacketPayload {

    public static final Type<SyncMobOriginPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_mob_origin"));

    public static final StreamCodec<FriendlyByteBuf, SyncMobOriginPayload> STREAM_CODEC =
        StreamCodec.of(SyncMobOriginPayload::encode, SyncMobOriginPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncMobOriginPayload p) {
        buf.writeVarInt(p.entityId);
        buf.writeOptional(p.originId, FriendlyByteBuf::writeResourceLocation);
    }

    private static SyncMobOriginPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Optional<ResourceLocation> origin = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        return new SyncMobOriginPayload(id, origin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
