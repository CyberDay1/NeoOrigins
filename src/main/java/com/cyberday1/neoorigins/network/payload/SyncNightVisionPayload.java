package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → owning client: the authoritative value of that player's night-vision
 * master switch (the dedicated "Toggle Night Vision" keybind).
 *
 * <p>Sent on login, on respawn/dimension change, and after every toggle. The
 * server-side status effect syncs itself through vanilla, so this payload exists
 * for the client-only consumers of the flag: the {@code enhanced_vision}
 * brightness boost in {@code LightTextureMixin}, which has no server effect to
 * read.
 *
 * <p>Unlike most sync payloads this one is player-private — no other client
 * needs to know, and night vision is not visible on another player's body.
 */
public record SyncNightVisionPayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<SyncNightVisionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_night_vision"));

    public static final StreamCodec<FriendlyByteBuf, SyncNightVisionPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.enabled()),
            buf -> new SyncNightVisionPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
