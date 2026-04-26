package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPickItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPickItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundPickItemPacket::write, ServerboundPickItemPacket::new
    );
    private final int slot;

    public ServerboundPickItemPacket(int p_134225_) {
        this.slot = p_134225_;
    }

    private ServerboundPickItemPacket(FriendlyByteBuf p_179704_) {
        this.slot = p_179704_.readVarInt();
    }

    private void write(FriendlyByteBuf p_134234_) {
        p_134234_.writeVarInt(this.slot);
    }

    @Override
    public PacketType<ServerboundPickItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_PICK_ITEM;
    }

    public void handle(ServerGamePacketListener p_134231_) {
        p_134231_.handlePickItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
