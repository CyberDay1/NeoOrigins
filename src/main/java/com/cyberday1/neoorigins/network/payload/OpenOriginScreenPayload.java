package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the origin selection screen on the client. {@code scopedLayers}, when
 * non-empty, restricts the picker to that allowlist of layers (a scoped picker —
 * {@link com.cyberday1.neoorigins.network.NeoOriginsNetwork#beginLayerPicker}, the
 * Orb of Class, {@code /origin gui <player> <layer>}). An empty list = unscoped.
 */
public record OpenOriginScreenPayload(boolean isOrb, boolean forceReselect, List<ResourceLocation> scopedLayers)
        implements CustomPacketPayload {

    public static final Type<OpenOriginScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "open_origin_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenOriginScreenPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.isOrb());
                buf.writeBoolean(payload.forceReselect());
                buf.writeInt(payload.scopedLayers().size());
                for (ResourceLocation id : payload.scopedLayers()) buf.writeResourceLocation(id);
            },
            buf -> {
                boolean isOrb = buf.readBoolean();
                boolean forceReselect = buf.readBoolean();
                int n = buf.readInt();
                List<ResourceLocation> scoped = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) scoped.add(buf.readResourceLocation());
                return new OpenOriginScreenPayload(isOrb, forceReselect, scoped);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
