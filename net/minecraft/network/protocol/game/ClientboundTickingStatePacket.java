package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.TickRateManager;

public record ClientboundTickingStatePacket(float tickRate, boolean isFrozen) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTickingStatePacket> STREAM_CODEC = Packet.codec(
        ClientboundTickingStatePacket::write, ClientboundTickingStatePacket::new
    );

    private ClientboundTickingStatePacket(FriendlyByteBuf p_309182_) {
        this(p_309182_.readFloat(), p_309182_.readBoolean());
    }

    public static ClientboundTickingStatePacket from(TickRateManager p_308984_) {
        return new ClientboundTickingStatePacket(p_308984_.tickrate(), p_308984_.isFrozen());
    }

    private void write(FriendlyByteBuf p_309152_) {
        p_309152_.writeFloat(this.tickRate);
        p_309152_.writeBoolean(this.isFrozen);
    }

    @Override
    public PacketType<ClientboundTickingStatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_TICKING_STATE;
    }

    public void handle(ClientGamePacketListener p_309074_) {
        p_309074_.handleTickingState(this);
    }
}
