package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client, to ONE player. The complete set of entity ids that the
 * receiving player's {@code neoorigins:entity_glow} power currently
 * shows glowing to them.
 *
 * <p>Unlike {@link SyncInvisibilityArmorPayload} (broadcast to everyone tracking a
 * player, because it describes how that player looks to others) this is private to
 * the holder: it describes what THEY can see, so no other client has any use for
 * it. It is a full replacement rather than a delta — the set is small, bounded, and
 * re-sent only when it actually changes, and a full replace can never drift out of
 * sync after a dropped delta.
 *
 * <p>See {@code PreventEntityRenderPower} for why the condition is evaluated on the
 * server instead of on the client.
 */
public record SyncGlowingEntitiesPayload(
    List<Integer> entityIds
) implements CustomPacketPayload {

    public static final Type<SyncGlowingEntitiesPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_glowing_entities"));

    public static final StreamCodec<FriendlyByteBuf, SyncGlowingEntitiesPayload> STREAM_CODEC =
        StreamCodec.of(SyncGlowingEntitiesPayload::encode, SyncGlowingEntitiesPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncGlowingEntitiesPayload payload) {
        buf.writeVarInt(payload.entityIds().size());
        for (int id : payload.entityIds()) buf.writeVarInt(id);
    }

    private static SyncGlowingEntitiesPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readVarInt());
        return new SyncGlowingEntitiesPayload(ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
