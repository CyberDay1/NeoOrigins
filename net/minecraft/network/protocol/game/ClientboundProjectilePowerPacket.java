package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundProjectilePowerPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundProjectilePowerPacket> STREAM_CODEC = Packet.codec(
        ClientboundProjectilePowerPacket::write, ClientboundProjectilePowerPacket::new
    );
    private final int id;
    private final double accelerationPower;

    public ClientboundProjectilePowerPacket(int p_339664_, double p_339660_) {
        this.id = p_339664_;
        this.accelerationPower = p_339660_;
    }

    private ClientboundProjectilePowerPacket(FriendlyByteBuf p_339617_) {
        this.id = p_339617_.readVarInt();
        this.accelerationPower = p_339617_.readDouble();
    }

    private void write(FriendlyByteBuf p_339614_) {
        p_339614_.writeVarInt(this.id);
        p_339614_.writeDouble(this.accelerationPower);
    }

    @Override
    public PacketType<ClientboundProjectilePowerPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER;
    }

    public void handle(ClientGamePacketListener p_339610_) {
        p_339610_.handleProjectilePowerPacket(this);
    }

    public int getId() {
        return this.id;
    }

    public double getAccelerationPower() {
        return this.accelerationPower;
    }
}
