package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client→server when the editor GUI toggles a power by identifier rather than by
 * keybind slot. The server handler must verify the player actually has the power granted
 * before firing onActivated.
 */
public record EditorTogglePowerPayload(ResourceLocation powerId) implements CustomPacketPayload {

    public static final Type<EditorTogglePowerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "editor_toggle_power"));

    public static final StreamCodec<FriendlyByteBuf, EditorTogglePowerPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeResourceLocation(payload.powerId()),
            buf -> new EditorTogglePowerPayload(buf.readResourceLocation())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
