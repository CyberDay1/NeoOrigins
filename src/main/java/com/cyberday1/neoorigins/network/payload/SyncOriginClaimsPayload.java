package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
        Map<ResourceLocation, Map<ResourceLocation, String>> claims) implements CustomPacketPayload {

    public static final Type<SyncOriginClaimsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_origin_claims"));

    public static final StreamCodec<FriendlyByteBuf, SyncOriginClaimsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.claims().size());
                payload.claims().forEach((layer, origins) -> {
                    buf.writeResourceLocation(layer);
                    buf.writeVarInt(origins.size());
                    origins.forEach((origin, owner) -> {
                        buf.writeResourceLocation(origin);
                        buf.writeUtf(owner);
                    });
                });
            },
            buf -> {
                int layerCount = buf.readVarInt();
                Map<ResourceLocation, Map<ResourceLocation, String>> claims = new HashMap<>(layerCount);
                for (int i = 0; i < layerCount; i++) {
                    ResourceLocation layer = buf.readResourceLocation();
                    int originCount = buf.readVarInt();
                    Map<ResourceLocation, String> origins = new HashMap<>(originCount);
                    for (int j = 0; j < originCount; j++) {
                        ResourceLocation origin = buf.readResourceLocation();
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
