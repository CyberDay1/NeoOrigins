package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundChunkBatchFinishedPacket(int batchSize) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundChunkBatchFinishedPacket> STREAM_CODEC = Packet.codec(
        ClientboundChunkBatchFinishedPacket::write, ClientboundChunkBatchFinishedPacket::new
    );

    private ClientboundChunkBatchFinishedPacket(FriendlyByteBuf p_296389_) {
        this(p_296389_.readVarInt());
    }

    private void write(FriendlyByteBuf p_294533_) {
        p_294533_.writeVarInt(this.batchSize);
    }

    @Override
    public PacketType<ClientboundChunkBatchFinishedPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_FINISHED;
    }

    public void handle(ClientGamePacketListener p_296221_) {
        p_296221_.handleChunkBatchFinished(this);
    }
}
