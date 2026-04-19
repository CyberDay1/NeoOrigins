package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Server → client. Pushes the full set of powers currently granted to the local player,
 * plus per-power toggle state (for toggleable powers). Non-toggleable powers that are
 * granted have value {@code true}.
 *
 * Sent on login, respawn, origin change, and toggle change. This is the client-side
 * source of truth for "does my player have power X right now?" — used by client-predicted
 * movement mixins (wall-climb, flight, climbing, etc.) and by the origin editor / power
 * tester GUI.
 */
public record SyncActivePowersPayload(
    Map<ResourceLocation, Boolean> powers
) implements CustomPacketPayload {

    public static final Type<SyncActivePowersPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_active_powers"));

    public static final StreamCodec<FriendlyByteBuf, SyncActivePowersPayload> STREAM_CODEC =
        StreamCodec.of(SyncActivePowersPayload::encode, SyncActivePowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncActivePowersPayload payload) {
        buf.writeMap(payload.powers(),
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::writeBoolean);
    }

    private static SyncActivePowersPayload decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Boolean> powers = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            FriendlyByteBuf::readBoolean);
        return new SyncActivePowersPayload(powers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
