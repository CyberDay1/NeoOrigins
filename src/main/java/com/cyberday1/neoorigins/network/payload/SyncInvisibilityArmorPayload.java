package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client. Tells receiving clients whether player {@code entityId}
 * currently has the {@code neoorigins:invisibility} power active with
 * {@code render_armor: false} — i.e. whether their worn armor should be hidden.
 *
 * <p>Like {@link SyncPlayerMorphPayload} (and unlike {@link SyncActivePowersPayload},
 * which only reaches the owning player), this is broadcast to every client tracking
 * the affected player AND the player themselves, so the armor-hide is consistent
 * for every viewer. The receiving client stores it in
 * {@code ClientInvisibilityArmorState} keyed by entity id and the client armor-layer
 * mixin reads it while rendering the {@code HumanoidArmorLayer}.
 */
public record SyncInvisibilityArmorPayload(
    int entityId,
    boolean hideArmor
) implements CustomPacketPayload {

    public static final Type<SyncInvisibilityArmorPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_invisibility_armor"));

    public static final StreamCodec<FriendlyByteBuf, SyncInvisibilityArmorPayload> STREAM_CODEC =
        StreamCodec.of(SyncInvisibilityArmorPayload::encode, SyncInvisibilityArmorPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncInvisibilityArmorPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeBoolean(payload.hideArmor());
    }

    private static SyncInvisibilityArmorPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean hide = buf.readBoolean();
        return new SyncInvisibilityArmorPayload(id, hide);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
