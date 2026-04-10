package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client→server when the player presses the Class Skill keybind.
 * Activates the first active power from the class layer.
 */
public record ActivateClassPowerPayload() implements CustomPacketPayload {

    public static final Type<ActivateClassPowerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "activate_class_power"));

    public static final StreamCodec<FriendlyByteBuf, ActivateClassPowerPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {},
            buf -> new ActivateClassPowerPayload()
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
