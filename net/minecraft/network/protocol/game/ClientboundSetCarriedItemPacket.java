package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetCarriedItemPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetCarriedItemPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetCarriedItemPacket::write, ClientboundSetCarriedItemPacket::new
    );
    private final int slot;

    public ClientboundSetCarriedItemPacket(int p_133072_) {
        this.slot = p_133072_;
    }

    private ClientboundSetCarriedItemPacket(FriendlyByteBuf p_179280_) {
        this.slot = p_179280_.readByte();
    }

    private void write(FriendlyByteBuf p_133081_) {
        p_133081_.writeByte(this.slot);
    }

    @Override
    public PacketType<ClientboundSetCarriedItemPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_CARRIED_ITEM;
    }

    public void handle(ClientGamePacketListener p_133078_) {
        p_133078_.handleSetCarriedItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
