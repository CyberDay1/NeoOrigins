package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts a single player's per-layer origin map to every other client so
 * each viewer can render that player's fur overlay. An empty {@code origins}
 * map signals "forget this player" (e.g. logout) and triggers cache eviction
 * on the receiver.
 *
 * <p>Symmetric with {@link SyncOriginsPayload}, but carries the subject
 * player's UUID instead of implicitly targeting the receiver. Sent on login
 * (for each existing player, to the joining player), on origin change (to
 * everyone), and on logout (cleared entry to everyone).</p>
 */
public record SyncRemoteOriginsPayload(
    UUID playerUuid,
    Map<ResourceLocation, ResourceLocation> origins
) implements CustomPacketPayload {

    public static final Type<SyncRemoteOriginsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_remote_origins"));

    public static final StreamCodec<FriendlyByteBuf, SyncRemoteOriginsPayload> STREAM_CODEC =
        StreamCodec.of(SyncRemoteOriginsPayload::encode, SyncRemoteOriginsPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncRemoteOriginsPayload payload) {
        buf.writeUUID(payload.playerUuid());
        buf.writeMap(payload.origins(),
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::writeResourceLocation);
    }

    private static SyncRemoteOriginsPayload decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        Map<ResourceLocation, ResourceLocation> origins = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            FriendlyByteBuf::readResourceLocation);
        return new SyncRemoteOriginsPayload(uuid, origins);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
