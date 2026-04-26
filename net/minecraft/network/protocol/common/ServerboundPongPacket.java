package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPongPacket implements Packet<ServerCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPongPacket> STREAM_CODEC = Packet.codec(
        ServerboundPongPacket::write, ServerboundPongPacket::new
    );
    private final int id;

    public ServerboundPongPacket(int p_295752_) {
        this.id = p_295752_;
    }

    private ServerboundPongPacket(FriendlyByteBuf p_295215_) {
        this.id = p_295215_.readInt();
    }

    private void write(FriendlyByteBuf p_295843_) {
        p_295843_.writeInt(this.id);
    }

    @Override
    public PacketType<ServerboundPongPacket> type() {
        return CommonPacketTypes.SERVERBOUND_PONG;
    }

    public void handle(ServerCommonPacketListener p_295714_) {
        p_295714_.handlePong(this);
    }

    public int getId() {
        return this.id;
    }
}
