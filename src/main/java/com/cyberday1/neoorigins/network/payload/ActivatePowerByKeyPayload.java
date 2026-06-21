package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client → server when a player presses a hotkey assigned to a
 * pack-declared {@code "key": "translation.key.id"} binding.
 *
 * <p>The {@code held} flag distinguishes a fresh press from continuous
 * "still held this tick" reports; the server uses it to apply edge vs.
 * continuous semantics from the registry (the wire never carries the
 * continuous flag itself — that's pure server state).
 *
 * @param translationKey the declared translation key (e.g. {@code "deanos_origins.key.origins.2"})
 * @param held           true if the client is reporting a "still held" sample
 *                       (continuous bindings); false for a one-shot press
 */
public record ActivatePowerByKeyPayload(String translationKey, boolean held)
    implements CustomPacketPayload {

    public static final Type<ActivatePowerByKeyPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "activate_power_by_key"));

    public static final StreamCodec<FriendlyByteBuf, ActivatePowerByKeyPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.translationKey, 256); buf.writeBoolean(p.held); },
            buf -> new ActivatePowerByKeyPayload(buf.readUtf(256), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
