package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundContainerSlotStateChangedPacket(int slotId, int containerId, boolean newState) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundContainerSlotStateChangedPacket> STREAM_CODEC = Packet.codec(
        ServerboundContainerSlotStateChangedPacket::write, ServerboundContainerSlotStateChangedPacket::new
    );

    private ServerboundContainerSlotStateChangedPacket(FriendlyByteBuf p_307271_) {
        this(p_307271_.readVarInt(), p_307271_.readVarInt(), p_307271_.readBoolean());
    }

    private void write(FriendlyByteBuf p_307330_) {
        p_307330_.writeVarInt(this.slotId);
        p_307330_.writeVarInt(this.containerId);
        p_307330_.writeBoolean(this.newState);
    }

    @Override
    public PacketType<ServerboundContainerSlotStateChangedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CONTAINER_SLOT_STATE_CHANGED;
    }

    public void handle(ServerGamePacketListener p_307397_) {
        p_307397_.handleContainerSlotStateChanged(this);
    }
}
