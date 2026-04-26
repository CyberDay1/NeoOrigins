package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.TickRateManager;

public record ClientboundTickingStepPacket(int tickSteps) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTickingStepPacket> STREAM_CODEC = Packet.codec(
        ClientboundTickingStepPacket::write, ClientboundTickingStepPacket::new
    );

    private ClientboundTickingStepPacket(FriendlyByteBuf p_309129_) {
        this(p_309129_.readVarInt());
    }

    public static ClientboundTickingStepPacket from(TickRateManager p_308913_) {
        return new ClientboundTickingStepPacket(p_308913_.frozenTicksToRun());
    }

    private void write(FriendlyByteBuf p_309179_) {
        p_309179_.writeVarInt(this.tickSteps);
    }

    @Override
    public PacketType<ClientboundTickingStepPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TICKING_STEP;
    }

    public void handle(ClientGamePacketListener p_309086_) {
        p_309086_.handleTickingStep(this);
    }
}
