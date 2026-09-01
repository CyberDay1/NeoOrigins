package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Server → client. Asks receiving clients to trigger (or stop) a triggerable
 * animation on the morph dummy standing in for player {@code entityId} — the
 * wire form of {@code neoorigins:trigger_morph_animation}.
 *
 * <p>The action itself only runs server-side ({@code EntityAction} is handed a
 * {@code ServerPlayer}), but the dummy is a purely client-side entity that the
 * server has no handle on, and every nearby player has their own copy of it.
 * So the verb is server-triggered and client-executed, broadcast with
 * {@code sendToPlayersTrackingEntityAndSelf} exactly like
 * {@link SyncPlayerMorphPayload}, so a morph's animation is visible to
 * onlookers and not just to its owner.
 *
 * <p>{@code controller} empty means "let the animation library search every
 * controller" — GeckoLib's one-arg {@code tryTriggerAnimation} path, which it
 * takes when the controller name is null.
 */
public record TriggerMorphAnimationPayload(
    int entityId,
    Optional<String> controller,
    String animation,
    boolean stop
) implements CustomPacketPayload {

    public static final Type<TriggerMorphAnimationPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "trigger_morph_animation"));

    public static final StreamCodec<FriendlyByteBuf, TriggerMorphAnimationPayload> STREAM_CODEC =
        StreamCodec.of(TriggerMorphAnimationPayload::encode, TriggerMorphAnimationPayload::decode);

    private static void encode(FriendlyByteBuf buf, TriggerMorphAnimationPayload payload) {
        buf.writeVarInt(payload.entityId());
        buf.writeOptional(payload.controller(), FriendlyByteBuf::writeUtf);
        buf.writeUtf(payload.animation());
        buf.writeBoolean(payload.stop());
    }

    private static TriggerMorphAnimationPayload decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Optional<String> controller = buf.readOptional(FriendlyByteBuf::readUtf);
        String animation = buf.readUtf();
        boolean stop = buf.readBoolean();
        return new TriggerMorphAnimationPayload(id, controller, animation, stop);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
