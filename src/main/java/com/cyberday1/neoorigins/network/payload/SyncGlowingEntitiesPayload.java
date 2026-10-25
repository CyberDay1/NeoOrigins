package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server to client, to ONE player: every entity the receiving player's
 * {@code neoorigins:entity_glow} powers outline for them, with the outline
 * colour for each ({@link #TEAM_COLOR} for vanilla's team colour). Private to
 * the holder and a full replacement, like {@link SyncHiddenEntitiesPayload};
 * see {@code PreventEntityRenderPower} for why the server decides.
 */
public record SyncGlowingEntitiesPayload(Map<Integer, Integer> colorsById) implements CustomPacketPayload {

    /** Colour value meaning "use the entity's vanilla outline colour". */
    public static final int TEAM_COLOR = -1;

    public static final Type<SyncGlowingEntitiesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_glowing_entities"));

    public static final StreamCodec<FriendlyByteBuf, SyncGlowingEntitiesPayload> STREAM_CODEC =
        StreamCodec.of(SyncGlowingEntitiesPayload::encode, SyncGlowingEntitiesPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncGlowingEntitiesPayload payload) {
        buf.writeVarInt(payload.colorsById().size());
        payload.colorsById().forEach((id, color) -> {
            buf.writeVarInt(id);
            buf.writeInt(color);
        });
    }

    private static SyncGlowingEntitiesPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, Integer> out = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) out.put(buf.readVarInt(), buf.readInt());
        return new SyncGlowingEntitiesPayload(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
