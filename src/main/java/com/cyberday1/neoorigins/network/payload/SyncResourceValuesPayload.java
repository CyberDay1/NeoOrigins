package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight value-only resource sync: resource key → current value, with an
 * optional per-key {@code max} override for bars whose maximum is dynamic.
 *
 * <p>Sent on the high-frequency paths (the 10-tick dirty sync and immediate
 * value mutations from actions/commands) where only values change. Static
 * display metadata (label, bounds, color, sprite/FX config) travels in the
 * full {@link SyncResourcePayload}, which is reserved for the chokepoints
 * that can create or remove bars: login/origin re-push, power grant/revoke
 * and datapack reload.
 *
 * <p><b>Dynamic max ({@code maxes}):</b> for an externally-backed bar (today
 * {@code backing: irons_spellbooks:mana}) the maximum is not a static
 * author-declared number — it is Iron's live max-mana ATTRIBUTE, which moves
 * with gear / level / effects. The full sync only carries {@code max} once, so
 * a gear swap would leave the HUD scaling against a stale denominator. This
 * value-only payload therefore also carries the CURRENT max for those bars in a
 * second, separately length-prefixed map. Non-backed bars put nothing in
 * {@code maxes}: the map is empty and its wire encoding is a single {@code 0}
 * varint, so a client that only holds internally-stored bars sees an identical
 * (aside from that trailing 0) packet and keeps its last full-sync max.
 *
 * <p>The client applies these values onto entries it already knows about and
 * ignores unknown keys — every entry-creation path is covered by a full sync,
 * and packets on a connection are ordered, so an unknown key can only be a
 * harmless ordering edge that the next full sync resolves.
 */
public record SyncResourceValuesPayload(Map<String, Integer> values, Map<String, Integer> maxes)
        implements CustomPacketPayload {

    /** Back-compat constructor for value-only callers (no dynamic max). */
    public SyncResourceValuesPayload(Map<String, Integer> values) {
        this(values, Map.of());
    }

    public static final Type<SyncResourceValuesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_resource_values"));

    public static final StreamCodec<FriendlyByteBuf, SyncResourceValuesPayload> STREAM_CODEC =
        StreamCodec.of(SyncResourceValuesPayload::write, SyncResourceValuesPayload::read);

    private static void write(FriendlyByteBuf buf, SyncResourceValuesPayload payload) {
        buf.writeVarInt(payload.values.size());
        for (var e : payload.values.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        // Second length-prefixed map: dynamic-max overrides (empty for non-backed
        // bars → just a trailing 0 varint).
        buf.writeVarInt(payload.maxes.size());
        for (var e : payload.maxes.entrySet()) {
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
        int maxSize = buf.readVarInt();
        Map<String, Integer> maxMap = new HashMap<>(maxSize);
        for (int i = 0; i < maxSize; i++) {
            String key = buf.readUtf();
            maxMap.put(key, buf.readVarInt());
        }
        return new SyncResourceValuesPayload(map, maxMap);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
