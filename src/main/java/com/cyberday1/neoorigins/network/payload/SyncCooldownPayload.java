package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * @param icon      optional cooldown-HUD icon (item id or {@code .png} texture
 *                  path) declared on the power; empty = render the plain bar.
 * @param countdown true if the power asked for remaining seconds drawn on the icon.
 */
public record SyncCooldownPayload(int slot, int totalTicks, int remainingTicks,
                                  String icon, boolean countdown) implements CustomPacketPayload {

    public static final Type<SyncCooldownPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, SyncCooldownPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.slot());
                buf.writeVarInt(payload.totalTicks());
                buf.writeVarInt(payload.remainingTicks());
                buf.writeUtf(payload.icon());
                buf.writeBoolean(payload.countdown());
            },
            buf -> new SyncCooldownPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
