package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.syncher.SynchedEntityData;

public record ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> packedItems) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEntityDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityDataPacket::write, ClientboundSetEntityDataPacket::new
    );
    public static final int EOF_MARKER = 255;

    private ClientboundSetEntityDataPacket(RegistryFriendlyByteBuf p_319996_) {
        this(p_319996_.readVarInt(), unpack(p_319996_));
    }

    private static void pack(List<SynchedEntityData.DataValue<?>> p_253940_, RegistryFriendlyByteBuf p_320247_) {
        for (SynchedEntityData.DataValue<?> datavalue : p_253940_) {
            datavalue.write(p_320247_);
        }

        p_320247_.writeByte(255);
    }

    private static List<SynchedEntityData.DataValue<?>> unpack(RegistryFriendlyByteBuf p_320523_) {
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

        int i;
        while ((i = p_320523_.readUnsignedByte()) != 255) {
            list.add(SynchedEntityData.DataValue.read(p_320523_, i));
        }

        return list;
    }

    private void write(RegistryFriendlyByteBuf p_320560_) {
        p_320560_.writeVarInt(this.id);
        pack(this.packedItems, p_320560_);
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA;
    }

    public void handle(ClientGamePacketListener p_133155_) {
        p_133155_.handleSetEntityData(this);
    }
}
