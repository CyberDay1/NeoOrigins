package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight value-only resource sync: resource key → current value.
 *
 * <p>Sent on the high-frequency paths (the 10-tick dirty sync and immediate
 * value mutations from actions/commands) where only values change. Static
 * display metadata (label, bounds, color, sprite/FX config) travels in the
 * full {@link SyncResourcePayload}, which is reserved for the chokepoints
 * that can create or remove bars: login/origin re-push, power grant/revoke
 * and datapack reload.
 *
 * <p>The client applies these values onto entries it already knows about and
 * ignores unknown keys — every entry-creation path is covered by a full sync,
 * and packets on a connection are ordered, so an unknown key can only be a
 * harmless ordering edge that the next full sync resolves.
 */
public record SyncResourceValuesPayload(Map<String, Integer> values) implements CustomPacketPayload {

    public static final Type<SyncResourceValuesPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_resource_values"));

    public static final StreamCodec<FriendlyByteBuf, SyncResourceValuesPayload> STREAM_CODEC =
        StreamCodec.of(SyncResourceValuesPayload::write, SyncResourceValuesPayload::read);

    private static void write(FriendlyByteBuf buf, SyncResourceValuesPayload payload) {
        buf.writeVarInt(payload.values.size());
        for (var e : payload.values.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static SyncResourceValuesPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Integer> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            map.put(key, buf.readVarInt());
        }
        return new SyncResourceValuesPayload(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
