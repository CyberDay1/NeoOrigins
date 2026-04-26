package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundBlockEntityTagQueryPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundBlockEntityTagQueryPacket> STREAM_CODEC = Packet.codec(
        ServerboundBlockEntityTagQueryPacket::write, ServerboundBlockEntityTagQueryPacket::new
    );
    private final int transactionId;
    private final BlockPos pos;

    public ServerboundBlockEntityTagQueryPacket(int p_320516_, BlockPos p_320060_) {
        this.transactionId = p_320516_;
        this.pos = p_320060_;
    }

    private ServerboundBlockEntityTagQueryPacket(FriendlyByteBuf p_319948_) {
        this.transactionId = p_319948_.readVarInt();
        this.pos = p_319948_.readBlockPos();
    }

    private void write(FriendlyByteBuf p_320108_) {
        p_320108_.writeVarInt(this.transactionId);
        p_320108_.writeBlockPos(this.pos);
    }

    @Override
    public PacketType<ServerboundBlockEntityTagQueryPacket> type() {
        return GamePacketTypes.SERVERBOUND_BLOCK_ENTITY_TAG_QUERY;
    }

    public void handle(ServerGamePacketListener p_320729_) {
        p_320729_.handleBlockEntityTagQuery(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
