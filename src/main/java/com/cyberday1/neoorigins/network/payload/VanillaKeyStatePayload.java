package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client→server whenever the real held-state of a vanilla input key that
 * compat active_self powers can bind to (USE / ATTACK) changes.
 *
 * <p>The server has no reliable native signal for these as <i>activation</i>
 * keys: {@code key.attack} was inferred from {@code player.swinging} (only
 * streams while actually mining/hitting — a hold on air swings once), and
 * {@code key.use} from a right-click interaction event (only fires when there's
 * an item to use or a block/entity to target). So an Apoli {@code key.use} /
 * {@code key.attack} active_self power simply didn't fire when the key was held
 * with nothing under the crosshair. This payload reports the genuine physical
 * key state so {@link com.cyberday1.neoorigins.compat.CompatPlayerState} can
 * answer the onTick poll truthfully.
 *
 * <p>Sent on edges only (press/release), not every tick: the server keeps the
 * last reported state until the next change.
 */
public record VanillaKeyStatePayload(boolean useDown, boolean attackDown) implements CustomPacketPayload {

    public static final Type<VanillaKeyStatePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "vanilla_key_state"));

    public static final StreamCodec<FriendlyByteBuf, VanillaKeyStatePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.useDown());
                buf.writeBoolean(payload.attackDown());
            },
            buf -> new VanillaKeyStatePayload(buf.readBoolean(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
