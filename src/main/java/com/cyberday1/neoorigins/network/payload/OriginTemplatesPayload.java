package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server→client snapshot of every origin currently loaded on the server,
 * shipped as the JSON shape produced by {@code OriginTemplates.toJson} so the
 * in-game creator's "Load template" picker has data to show. Pushed once
 * alongside {@link OpenEditorScreenPayload} when the creator is opened, and
 * cached in {@code ClientTemplateCache} until the next reopen.
 *
 * <p>Body can be hundreds of KB on packs with many origins — use the wide
 * UTF limit on read/write so we don't get a misleading "string too large"
 * decode failure on the picker.
 */
public record OriginTemplatesPayload(String json) implements CustomPacketPayload {

    public static final Type<OriginTemplatesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "origin_templates"));

    /** 4 MiB UTF cap — comfortably covers a pack with hundreds of origins,
     *  but bounded so a corrupted length field can't allocate an 8 MiB string. */
    private static final int MAX_UTF = 4 * 1024 * 1024;

    public static final StreamCodec<FriendlyByteBuf, OriginTemplatesPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.json(), MAX_UTF),
            buf -> new OriginTemplatesPayload(buf.readUtf(MAX_UTF))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
