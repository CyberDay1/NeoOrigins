package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client→server when the player presses a Skill keybind.
 * {@code slot} is 0-based: 0 = Skill 1, 1 = Skill 2, etc.
 */
public record ActivatePowerPayload(int slot) implements CustomPacketPayload {

    public static final Type<ActivatePowerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "activate_power"));

    public static final StreamCodec<FriendlyByteBuf, ActivatePowerPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.slot()),
            buf -> new ActivatePowerPayload(buf.readByte())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
