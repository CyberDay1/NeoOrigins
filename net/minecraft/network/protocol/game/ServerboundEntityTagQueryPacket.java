package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundEntityTagQueryPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundEntityTagQueryPacket> STREAM_CODEC = Packet.codec(
        ServerboundEntityTagQueryPacket::write, ServerboundEntityTagQueryPacket::new
    );
    private final int transactionId;
    private final int entityId;

    public ServerboundEntityTagQueryPacket(int p_319836_, int p_319857_) {
        this.transactionId = p_319836_;
        this.entityId = p_319857_;
    }

    private ServerboundEntityTagQueryPacket(FriendlyByteBuf p_319956_) {
        this.transactionId = p_319956_.readVarInt();
        this.entityId = p_319956_.readVarInt();
    }

    private void write(FriendlyByteBuf p_319826_) {
        p_319826_.writeVarInt(this.transactionId);
        p_319826_.writeVarInt(this.entityId);
    }

    @Override
    public PacketType<ServerboundEntityTagQueryPacket> type() {
        return GamePacketTypes.SERVERBOUND_ENTITY_TAG_QUERY;
    }

    public void handle(ServerGamePacketListener p_320580_) {
        p_320580_.handleEntityTagQuery(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public int getEntityId() {
        return this.entityId;
    }
}
