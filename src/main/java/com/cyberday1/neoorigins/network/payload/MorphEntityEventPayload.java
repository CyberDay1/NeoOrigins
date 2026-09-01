package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client. Asks receiving clients to run {@code handleEntityEvent(byte)}
 * on the morph dummy standing in for player {@code entityId} — the wire form of
 * {@code neoorigins:morph_entity_event}.
 *
 * <p>This is deliberately <em>not</em> a vanilla {@code ClientboundEntityEventPacket}:
 * that one is addressed by entity id and resolved against the client's real
 * entity list, and the morph dummy is not in it. So the payload names the
 * <em>player</em>, and the client handler redirects the byte onto whichever dummy
 * it is currently drawing for them.
 *
 * <p>Broadcast with {@code sendToPlayersTrackingEntityAndSelf}, like
 * {@link SyncPlayerMorphPayload} — each client owns its own dummy instance, so
 * every observer needs the byte or only the acting player sees the effect.
 *
 * <p>The byte has already been validated at parse time (in range, and not
 * {@code EntityEvent.DEATH}); the handler re-checks rather than trusting the
 * wire, because a payload is an untrusted input path.
 */
public record MorphEntityEventPayload(
    int entityId,
    byte event
) implements CustomPacketPayload {

    public static final Type<MorphEntityEventPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "morph_entity_event"));

    public static final StreamCodec<FriendlyByteBuf, MorphEntityEventPayload> STREAM_CODEC =
        StreamCodec.of(MorphEntityEventPayload::encode, MorphEntityEventPayload::decode);

    private static void encode(FriendlyByteBuf buf, MorphEntityEventPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeByte(payload.event());
    }

    private static MorphEntityEventPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        byte event = buf.readByte();
        return new MorphEntityEventPayload(id, event);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
