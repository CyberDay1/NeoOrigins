package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetTimePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetTimePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetTimePacket::write, ClientboundSetTimePacket::new
    );
    private final long gameTime;
    private final long dayTime;

    public ClientboundSetTimePacket(long p_133349_, long p_133350_, boolean p_133351_) {
        this.gameTime = p_133349_;
        long i = p_133350_;
        if (!p_133351_) {
            i = -p_133350_;
            if (i == 0L) {
                i = -1L;
            }
        }

        this.dayTime = i;
    }

    private ClientboundSetTimePacket(FriendlyByteBuf p_179387_) {
        this.gameTime = p_179387_.readLong();
        this.dayTime = p_179387_.readLong();
    }

    private void write(FriendlyByteBuf p_133360_) {
        p_133360_.writeLong(this.gameTime);
        p_133360_.writeLong(this.dayTime);
    }

    @Override
    public PacketType<ClientboundSetTimePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_TIME;
    }

    public void handle(ClientGamePacketListener p_133357_) {
        p_133357_.handleSetTime(this);
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public long getDayTime() {
        return this.dayTime;
    }
}
