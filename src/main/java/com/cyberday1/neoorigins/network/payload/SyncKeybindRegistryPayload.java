package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sent server → client whenever the named-keybind registry changes
 * (login, datapack reload, custom-pack apply). The client uses this to
 * deterministically assign each declared translation key to an anonymous
 * KeyMapping slot from the hotkey pool, and to know which key id to put
 * on the wire when a slot is pressed.
 *
 * @param declaredKeys    sorted list of translation keys in registration order
 * @param continuousFlags one flag per key (same order); true = fire while held
 * @param powerToKey      power id → translation key, for in-game UI hints
 */
public record SyncKeybindRegistryPayload(
    List<String> declaredKeys,
    List<Boolean> continuousFlags,
    Map<ResourceLocation, String> powerToKey
) implements CustomPacketPayload {

    public static final Type<SyncKeybindRegistryPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_keybind_registry"));

    public static final StreamCodec<FriendlyByteBuf, SyncKeybindRegistryPayload> STREAM_CODEC =
        StreamCodec.of(SyncKeybindRegistryPayload::encode, SyncKeybindRegistryPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncKeybindRegistryPayload p) {
        // Defensive: declaredKeys and continuousFlags must be parallel arrays.
        // If they ever drift, the client decodes garbage flags. Sender should
        // build both in the same loop — this assert catches programmer error
        // before it lands on a wire.
        int n = p.declaredKeys.size();
        if (p.continuousFlags.size() != n) {
            throw new IllegalArgumentException(
                "SyncKeybindRegistryPayload: declaredKeys.size=" + n
                    + " != continuousFlags.size=" + p.continuousFlags.size());
        }
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(p.declaredKeys.get(i), 256);
            buf.writeBoolean(p.continuousFlags.get(i));
        }

        buf.writeVarInt(p.powerToKey.size());
        for (var entry : p.powerToKey.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue(), 256);
        }
    }

    private static SyncKeybindRegistryPayload decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> keys = new java.util.ArrayList<>(n);
        List<Boolean> flags = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keys.add(buf.readUtf(256));
            flags.add(buf.readBoolean());
        }

        int m = buf.readVarInt();
        Map<ResourceLocation, String> p2k = new HashMap<>(m);
        for (int i = 0; i < m; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String key = buf.readUtf(256);
            p2k.put(id, key);
        }

        return new SyncKeybindRegistryPayload(keys, flags, p2k);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
