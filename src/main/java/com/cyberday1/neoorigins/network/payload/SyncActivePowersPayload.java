package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Map;
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
    Set<String> capabilities
) implements CustomPacketPayload {

    public static final Type<SyncActivePowersPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_active_powers"));

    public static final StreamCodec<FriendlyByteBuf, SyncActivePowersPayload> STREAM_CODEC =
        StreamCodec.of(SyncActivePowersPayload::encode, SyncActivePowersPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncActivePowersPayload payload) {
        buf.writeMap(payload.powers(),
            FriendlyByteBuf::writeIdentifier,
            FriendlyByteBuf::writeBoolean);
        buf.writeVarInt(payload.capabilities().size());
        for (String cap : payload.capabilities()) {
            buf.writeUtf(cap);
        }
    }

    private static SyncActivePowersPayload decode(FriendlyByteBuf buf) {
        Map<Identifier, Boolean> powers = buf.readMap(
            FriendlyByteBuf::readIdentifier,
            FriendlyByteBuf::readBoolean);
        int capCount = buf.readVarInt();
        Set<String> capabilities = new HashSet<>(capCount);
        for (int i = 0; i < capCount; i++) {
            capabilities.add(buf.readUtf());
        }
        return new SyncActivePowersPayload(powers, capabilities);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
