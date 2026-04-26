package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundRespawnPacket(CommonPlayerSpawnInfo commonPlayerSpawnInfo, byte dataToKeep) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRespawnPacket> STREAM_CODEC = Packet.codec(
        ClientboundRespawnPacket::write, ClientboundRespawnPacket::new
    );
    public static final byte KEEP_ATTRIBUTE_MODIFIERS = 1;
    public static final byte KEEP_ENTITY_DATA = 2;
    public static final byte KEEP_ALL_DATA = 3;

    private ClientboundRespawnPacket(RegistryFriendlyByteBuf p_321588_) {
        this(new CommonPlayerSpawnInfo(p_321588_), p_321588_.readByte());
    }

    private void write(RegistryFriendlyByteBuf p_321615_) {
        this.commonPlayerSpawnInfo.write(p_321615_);
        p_321615_.writeByte(this.dataToKeep);
    }

    @Override
    public PacketType<ClientboundRespawnPacket> type() {
        return GamePacketTypes.CLIENTBOUND_RESPAWN;
    }

    public void handle(ClientGamePacketListener p_132951_) {
        p_132951_.handleRespawn(this);
    }

    public boolean shouldKeep(byte p_263573_) {
        return (this.dataToKeep & p_263573_) != 0;
    }
}
