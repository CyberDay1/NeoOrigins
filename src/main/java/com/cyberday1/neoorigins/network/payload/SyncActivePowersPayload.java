package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
    Map<Identifier, Boolean> powers,
    Set<String> capabilities,
    /** Each API-granted leaf power, mapped to the layer its grant names (empty for none). */
    Map<Identifier, Optional<Identifier>> grantLayers
) implements CustomPacketPayload {

    public static final Type<SyncActivePowersPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_active_powers"));

    public static final StreamCodec<FriendlyByteBuf, SyncActivePowersPayload> STREAM_CODEC =
        StreamCodec.of(SyncActivePowersPayload::encode, SyncActivePowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncActivePowersPayload payload) {
        buf.writeMap(payload.powers(),
            FriendlyByteBuf::writeIdentifier,
            FriendlyByteBuf::writeBoolean);
        NetworkCodecs.writeStringSet(buf, payload.capabilities());
        buf.writeMap(payload.grantLayers(),
            FriendlyByteBuf::writeIdentifier,
            (b, layer) -> {
                b.writeBoolean(layer.isPresent());
                layer.ifPresent(b::writeIdentifier);
            });
    }

    private static SyncActivePowersPayload decode(FriendlyByteBuf buf) {
        Map<Identifier, Boolean> powers = buf.readMap(
            FriendlyByteBuf::readIdentifier,
            FriendlyByteBuf::readBoolean);
        Set<String> capabilities = NetworkCodecs.readStringSet(buf);
        Map<Identifier, Optional<Identifier>> grantLayers = buf.readMap(
            FriendlyByteBuf::readIdentifier,
            b -> b.readBoolean() ? Optional.of(b.readIdentifier()) : Optional.empty());
        return new SyncActivePowersPayload(powers, capabilities, grantLayers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
