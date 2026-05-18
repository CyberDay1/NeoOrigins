package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server→client after a Save or Apply: success flag + a human message
 * (write summary, reload-done, or the rejection/validation reason) for the
 * creator screen to surface. Stored client-side in {@code ClientCreatorState}.
 */
public record CreatorResultPayload(boolean ok, String message) implements CustomPacketPayload {

    public static final Type<CreatorResultPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "creator_result"));

    public static final StreamCodec<FriendlyByteBuf, CreatorResultPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> { buf.writeBoolean(payload.ok()); buf.writeUtf(payload.message()); },
            buf -> new CreatorResultPayload(buf.readBoolean(), buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
