package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Tells the client which origins are locked (claimed by another player) in
 * unique-enforced layers. Keyed layer ID -&gt; origin ID -&gt; owner display name.
 * The server omits the receiving player's own claims and sends an empty map
 * when the player bypasses the lock (OP in creative), so the client can simply
 * grey out everything it receives.
 */
public record SyncOriginClaimsPayload(
        Map<Identifier, Map<Identifier, String>> claims) implements CustomPacketPayload {

    public static final Type<SyncOriginClaimsPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_origin_claims"));

    public static final StreamCodec<FriendlyByteBuf, SyncOriginClaimsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.claims().size());
                payload.claims().forEach((layer, origins) -> {
                    buf.writeIdentifier(layer);
                    buf.writeVarInt(origins.size());
                    origins.forEach((origin, owner) -> {
                        buf.writeIdentifier(origin);
                        buf.writeUtf(owner);
                    });
                });
            },
            buf -> {
                int layerCount = buf.readVarInt();
                Map<Identifier, Map<Identifier, String>> claims = new HashMap<>(layerCount);
                for (int i = 0; i < layerCount; i++) {
                    Identifier layer = buf.readIdentifier();
                    int originCount = buf.readVarInt();
                    Map<Identifier, String> origins = new HashMap<>(originCount);
                    for (int j = 0; j < originCount; j++) {
                        Identifier origin = buf.readIdentifier();
                        origins.put(origin, buf.readUtf());
                    }
                    claims.put(layer, origins);
                }
                return new SyncOriginClaimsPayload(claims);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
