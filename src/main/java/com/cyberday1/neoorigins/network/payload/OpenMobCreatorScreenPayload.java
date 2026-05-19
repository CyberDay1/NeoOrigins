package com.cyberday1.neoorigins.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S→C trampoline: open the Mob Origin Creator screen client-side. */
public record OpenMobCreatorScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenMobCreatorScreenPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "open_mob_creator_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenMobCreatorScreenPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new OpenMobCreatorScreenPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
