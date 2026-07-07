package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client. Tells receiving clients whether player {@code entityId}
 * currently has the {@code neoorigins:elytra_flight} power active with
 * {@code render_elytra: true} — i.e. whether a drawn elytra should appear on their
 * back while fall-flying — and, optionally, a custom texture id for that elytra.
 *
 * <p>Like {@link SyncInvisibilityArmorPayload} and {@link SyncPlayerMorphPayload}
 * (and unlike {@link SyncActivePowersPayload}, which only reaches the owning player),
 * this is broadcast to every client tracking the affected player AND the player
 * themselves, so the wings are consistent for every viewer. The receiving client
 * stores it in {@code ClientElytraFlightState} keyed by entity id and the client
 * elytra render layer reads it while rendering the player.
 *
 * <p>{@code texture} is empty ({@code ""}) to mean "render with the vanilla elytra
 * texture"; {@code render} false clears any wings from this power.
 */
public record SyncElytraFlightPayload(
    int entityId,
    boolean render,
    String texture
) implements CustomPacketPayload {

    public static final Type<SyncElytraFlightPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("neoorigins", "sync_elytra_flight"));

    public static final StreamCodec<FriendlyByteBuf, SyncElytraFlightPayload> STREAM_CODEC =
        StreamCodec.of(SyncElytraFlightPayload::encode, SyncElytraFlightPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncElytraFlightPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeBoolean(payload.render());
        buf.writeUtf(payload.texture());
    }

    private static SyncElytraFlightPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean render = buf.readBoolean();
        String texture = buf.readUtf();
        return new SyncElytraFlightPayload(id, render, texture);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
