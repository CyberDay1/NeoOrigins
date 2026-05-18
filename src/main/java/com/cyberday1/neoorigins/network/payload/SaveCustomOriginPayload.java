package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client→server to persist a draft. Carries the whole draft as one JSON
 * string (see {@code OriginDraftJson}) so no bespoke per-field codec is needed;
 * the server re-parses, gate-checks ({@code CreatorAccess}), writes via
 * {@code CustomPackWriter}, and replies {@link CreatorResultPayload}.
 *
 * <p>Save writes files only — reload is the separate {@link ApplyCustomPackPayload}
 * (locked UX: author controls the visible reload hitch).
 */
public record SaveCustomOriginPayload(String draftJson) implements CustomPacketPayload {

    public static final Type<SaveCustomOriginPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "save_custom_origin"));

    public static final StreamCodec<FriendlyByteBuf, SaveCustomOriginPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.draftJson()),
            buf -> new SaveCustomOriginPayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
