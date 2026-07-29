package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the player pressed the dedicated "Toggle Night Vision"
 * keybind. Carries no state — it is a request to FLIP, not an assertion of the
 * new value, so the server stays the sole owner of the flag and a desynced or
 * spoofed client can't pin night vision on/off. The server answers with a
 * {@link SyncNightVisionPayload} carrying the authoritative result.
 */
public record ToggleNightVisionPayload() implements CustomPacketPayload {

    public static final Type<ToggleNightVisionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "toggle_night_vision"));

    public static final StreamCodec<FriendlyByteBuf, ToggleNightVisionPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {}, buf -> new ToggleNightVisionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
