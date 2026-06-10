package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs all active resource bar state to the client in one packet.
 * Sent periodically from the server whenever resource values change,
 * and as a full snapshot on login / origin change.
 *
 * <p>Each entry carries the current value plus display metadata so the
 * client can render the bar without a separate config payload.
 */
public record SyncResourcePayload(Map<String, Entry> resources) implements CustomPacketPayload {

    public record Entry(int value, int min, int max, String label, int color,
                        int barIndex, int iconIndex, String spriteLocation,
                        String animated, int tint) {}

    public static final Type<SyncResourcePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_resource"));

    public static final StreamCodec<FriendlyByteBuf, SyncResourcePayload> STREAM_CODEC =
        StreamCodec.of(SyncResourcePayload::write, SyncResourcePayload::read);

    private static void write(FriendlyByteBuf buf, SyncResourcePayload payload) {
        buf.writeVarInt(payload.resources.size());
        for (var e : payload.resources.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().value());
            buf.writeVarInt(e.getValue().min());
            buf.writeVarInt(e.getValue().max());
            buf.writeUtf(e.getValue().label());
            buf.writeInt(e.getValue().color());
            buf.writeVarInt(e.getValue().barIndex());
            buf.writeVarInt(e.getValue().iconIndex());
            buf.writeUtf(e.getValue().spriteLocation());
            buf.writeUtf(e.getValue().animated());
            buf.writeInt(e.getValue().tint());
        }
    }

    private static SyncResourcePayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Entry> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            int value = buf.readVarInt();
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            String label = buf.readUtf();
            int color = buf.readInt();
            int barIndex = buf.readVarInt();
            int iconIndex = buf.readVarInt();
            String spriteLocation = buf.readUtf();
            String animated = buf.readUtf();
            int tint = buf.readInt();
            map.put(key, new Entry(value, min, max, label, color, barIndex, iconIndex, spriteLocation, animated, tint));
        }
        return new SyncResourcePayload(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
