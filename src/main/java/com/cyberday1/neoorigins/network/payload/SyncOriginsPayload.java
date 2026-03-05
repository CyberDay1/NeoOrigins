package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public record SyncOriginsPayload(
    Map<Identifier, Identifier> origins,
    boolean hadAllOrigins
) implements CustomPacketPayload {

    public static final Type<SyncOriginsPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_origins"));

    public static final StreamCodec<FriendlyByteBuf, SyncOriginsPayload> STREAM_CODEC =
        StreamCodec.of(SyncOriginsPayload::encode, SyncOriginsPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncOriginsPayload payload) {
        buf.writeMap(payload.origins(),
            FriendlyByteBuf::writeIdentifier,
            FriendlyByteBuf::writeIdentifier);
        buf.writeBoolean(payload.hadAllOrigins());
    }

    private static SyncOriginsPayload decode(FriendlyByteBuf buf) {
        Map<Identifier, Identifier> origins = buf.readMap(
            FriendlyByteBuf::readIdentifier,
            FriendlyByteBuf::readIdentifier);
        boolean hadAll = buf.readBoolean();
        return new SyncOriginsPayload(origins, hadAll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
