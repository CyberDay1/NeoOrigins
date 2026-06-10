package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Server → client. Tells receiving clients that player {@code entityId} is
 * currently morphed into the entity type {@code entityType} (from the
 * {@code neoorigins:entity_model} power's capability tag), or — when
 * {@code entityType} is empty — that the player is no longer morphed.
 *
 * <p>Unlike {@link SyncActivePowersPayload} (which only reaches the owning
 * player), this is broadcast to every client tracking the morphed player AND
 * the player themselves, so the morph is visible to everyone. The receiving
 * client stores it in {@code ClientMorphState} keyed by entity id and the
 * morph renderer reads it during {@code RenderPlayerEvent.Pre}.
 */
public record SyncPlayerMorphPayload(
    int entityId,
    Optional<Identifier> entityType
) implements CustomPacketPayload {

    public static final Type<SyncPlayerMorphPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_player_morph"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerMorphPayload> STREAM_CODEC =
        StreamCodec.of(SyncPlayerMorphPayload::encode, SyncPlayerMorphPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncPlayerMorphPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeOptional(payload.entityType(), FriendlyByteBuf::writeIdentifier);
    }

    private static SyncPlayerMorphPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Optional<Identifier> type = buf.readOptional(FriendlyByteBuf::readIdentifier);
        return new SyncPlayerMorphPayload(id, type);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
