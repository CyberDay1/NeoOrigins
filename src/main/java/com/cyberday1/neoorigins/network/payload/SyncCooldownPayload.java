package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncCooldownPayload(int slot, int totalTicks, int remainingTicks) implements CustomPacketPayload {

    public static final Type<SyncCooldownPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, SyncCooldownPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> { buf.writeByte(payload.slot()); buf.writeVarInt(payload.totalTicks()); buf.writeVarInt(payload.remainingTicks()); },
            buf -> new SyncCooldownPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
