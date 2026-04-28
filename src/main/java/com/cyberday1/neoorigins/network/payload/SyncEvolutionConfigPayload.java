package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client packet that syncs evolution config values on login.
 * Ensures the client displays correct thresholds, tier names, and
 * progress regardless of local config file state.
 */
public record SyncEvolutionConfigPayload(
    boolean enabled,
    int tier1Kills,
    int tier2Kills,
    int tier3Kills,
    int messageInterval,
    int currentKills,
    int currentTier
) implements CustomPacketPayload {

    public static final Type<SyncEvolutionConfigPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_evolution_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncEvolutionConfigPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.enabled());
                buf.writeVarInt(p.tier1Kills());
                buf.writeVarInt(p.tier2Kills());
                buf.writeVarInt(p.tier3Kills());
                buf.writeVarInt(p.messageInterval());
                buf.writeVarInt(p.currentKills());
                buf.writeVarInt(p.currentTier());
            },
            buf -> new SyncEvolutionConfigPayload(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
