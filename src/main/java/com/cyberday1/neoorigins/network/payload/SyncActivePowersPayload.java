package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server → client. Pushes the full set of powers currently granted to the local player,
 * plus per-power toggle state, plus the union of {@link com.cyberday1.neoorigins.api.power.PowerType#capabilities}
 * from powers that are currently active (granted AND not toggled off).
 *
 * Sent on login, respawn, origin change, and toggle change. This is the client-side
 * source of truth for "does my player have power X right now?" — used by client-predicted
 * movement mixins (wall-climb, flight, climbing, etc.) and by the origin editor / power
 * tester GUI.
 */
public record SyncActivePowersPayload(
    Map<ResourceLocation, Boolean> powers,
    Set<String> capabilities,
    /** Each API-granted leaf power, mapped to the layer its grant names (empty for none). */
    Map<ResourceLocation, Optional<ResourceLocation>> grantLayers
) implements CustomPacketPayload {

    public static final Type<SyncActivePowersPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_active_powers"));

    public static final StreamCodec<FriendlyByteBuf, SyncActivePowersPayload> STREAM_CODEC =
        StreamCodec.of(SyncActivePowersPayload::encode, SyncActivePowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncActivePowersPayload payload) {
        buf.writeMap(payload.powers(),
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::writeBoolean);
        NetworkCodecs.writeStringSet(buf, payload.capabilities());
        buf.writeMap(payload.grantLayers(),
            FriendlyByteBuf::writeResourceLocation,
            (b, layer) -> {
                b.writeBoolean(layer.isPresent());
                layer.ifPresent(b::writeResourceLocation);
            });
    }

    private static SyncActivePowersPayload decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Boolean> powers = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            FriendlyByteBuf::readBoolean);
        Set<String> capabilities = NetworkCodecs.readStringSet(buf);
        Map<ResourceLocation, Optional<ResourceLocation>> grantLayers = buf.readMap(
            FriendlyByteBuf::readResourceLocation,
            b -> b.readBoolean() ? Optional.of(b.readResourceLocation()) : Optional.empty());
        return new SyncActivePowersPayload(powers, capabilities, grantLayers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
