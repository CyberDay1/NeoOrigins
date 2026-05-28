package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight server -> client packet that delivers a live update of the
 * player's Essence Evolution progress (kill count + current tier).
 *
 * <p>Sent every kill, so this payload deliberately omits the static config
 * (thresholds, message interval, enabled flag) carried by
 * {@link SyncEvolutionConfigPayload} -- those only change on login / reload.
 */
public record SyncEvolutionProgressPayload(int kills, int tier) implements CustomPacketPayload {

    public static final Type<SyncEvolutionProgressPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_evolution_progress"));

    public static final StreamCodec<FriendlyByteBuf, SyncEvolutionProgressPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kills());
                buf.writeVarInt(p.tier());
            },
            buf -> new SyncEvolutionProgressPayload(buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
