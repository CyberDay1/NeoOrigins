package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client packet that tells the client which UI theme the active
 * datapack stack has declared. Sourced from
 * {@link com.cyberday1.neoorigins.data.ActiveThemeManager}; consumed by
 * {@link com.cyberday1.neoorigins.client.theme.ActiveThemeRegistry}.
 *
 * <p>{@code themeId} may be empty ({@code ""}) to indicate "no datapack
 * declared a theme — fall back to the default". The empty-string sentinel
 * keeps the stream codec simple (no nullable wrapper).
 */
public record SyncActiveThemePayload(String themeId) implements CustomPacketPayload {

    public static final Type<SyncActiveThemePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_active_theme"));

    public static final StreamCodec<FriendlyByteBuf, SyncActiveThemePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.themeId() == null ? "" : p.themeId()),
            buf -> new SyncActiveThemePayload(buf.readUtf())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
