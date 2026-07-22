package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Server → a single observer. Tells the receiving client to evict player
 * {@code playerId} from its {@code ClientPlayerPowers} store — sent when the
 * observer stops tracking that player (out of range / removed), so the
 * Figura-facing per-player state can't go stale or grow unbounded. The observer
 * re-receives current state via {@link SyncPlayerPowersPayload} on the next
 * start-tracking.
 */
public record RemovePlayerPowersPayload(
    UUID playerId
) implements CustomPacketPayload {

    public static final Type<RemovePlayerPowersPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "remove_player_powers"));

    public static final StreamCodec<FriendlyByteBuf, RemovePlayerPowersPayload> STREAM_CODEC =
        StreamCodec.of(RemovePlayerPowersPayload::encode, RemovePlayerPowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, RemovePlayerPowersPayload payload) {
        buf.writeUUID(payload.playerId());
    }

    private static RemovePlayerPowersPayload decode(FriendlyByteBuf buf) {
        return new RemovePlayerPowersPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
