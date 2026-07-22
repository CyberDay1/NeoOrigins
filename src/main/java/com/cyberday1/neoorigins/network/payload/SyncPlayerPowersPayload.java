package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server → client. Broadcasts one player's full NeoOrigins state to every client
 * that can see them (all trackers AND the player themselves), so any observer can
 * read the state of any visible player — not just their own.
 *
 * <p>Unlike {@link SyncActivePowersPayload} (which reaches only the owning player
 * and drives client-predicted movement mixins), this is the per-player, all-viewer
 * mirror consumed by the Figura soft-dep API: a Figura avatar is scoped to a player
 * UUID, and its Lua script (running on every observer's client) asks
 * "what origin / powers / capabilities does the player this avatar belongs to have?".
 * Because Figura never talks to the MC server and its sandbox can't read data
 * attachments, the server must push this per-player state down to every client.
 *
 * <p>Keyed by the player's {@link UUID} on the receiving client (stored in
 * {@code ClientPlayerPowers}) — the UUID is stable across the entity-id churn a
 * dimension change causes and is exactly what a Figura {@code Avatar} exposes as
 * its owner, so no UUID→entity resolution is needed to answer a query. The
 * {@code entityId} rides along only as a convenience for id-keyed client lookups.
 *
 * <p>Broadcast on the same triggers as {@link #broadcast the per-viewer morph /
 * armor / elytra payloads}: origin change, toggle flip, dimension transition,
 * respawn, login, and — for a single late-joining observer — on start-tracking.
 * Evicted on stop-tracking / logout.
 *
 * @param entityId   the tracked player's entity id (broadcast-target convenience)
 * @param playerId   the tracked player's UUID — the client store key (Figura owner)
 * @param origins    layer id → chosen origin id, for that player's current selection
 * @param powers     power id → toggle state (true = active / toggled on) for every
 *                   granted power, dimension restrictions applied
 * @param capabilities union of capability tags from powers that are currently active
 *                    (granted, toggled on, and top-level condition satisfied)
 * @param evolutionTier the player's current server-side evolution tier (0 when
 *                    none), so tier-reactive Figura models resolve correctly on
 *                    every observer's client, not just the player's own
 */
public record SyncPlayerPowersPayload(
    int entityId,
    UUID playerId,
    Map<ResourceLocation, ResourceLocation> origins,
    Map<ResourceLocation, Boolean> powers,
    Set<String> capabilities,
    int evolutionTier
) implements CustomPacketPayload {

    public static final Type<SyncPlayerPowersPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_player_powers"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerPowersPayload> STREAM_CODEC =
        StreamCodec.of(SyncPlayerPowersPayload::encode, SyncPlayerPowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncPlayerPowersPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeUUID(payload.playerId());
        buf.writeMap(payload.origins(),
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(payload.powers(),
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::writeBoolean);
        NetworkCodecs.writeStringSet(buf, payload.capabilities());
        buf.writeVarInt(payload.evolutionTier());
    }

    private static SyncPlayerPowersPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        UUID playerId = buf.readUUID();
        Map<ResourceLocation, ResourceLocation> origins = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            FriendlyByteBuf::readResourceLocation);
        Map<ResourceLocation, Boolean> powers = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            FriendlyByteBuf::readBoolean);
        Set<String> capabilities = NetworkCodecs.readStringSet(buf);
        int evolutionTier = buf.readVarInt();
        return new SyncPlayerPowersPayload(id, playerId, origins, powers, capabilities, evolutionTier);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
