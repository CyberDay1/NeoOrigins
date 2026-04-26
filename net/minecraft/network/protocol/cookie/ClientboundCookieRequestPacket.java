package net.minecraft.network.protocol.cookie;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public record ClientboundCookieRequestPacket(ResourceLocation key) implements Packet<ClientCookiePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCookieRequestPacket> STREAM_CODEC = Packet.codec(
        ClientboundCookieRequestPacket::write, ClientboundCookieRequestPacket::new
    );

    private ClientboundCookieRequestPacket(FriendlyByteBuf p_320446_) {
        this(p_320446_.readResourceLocation());
    }

    private void write(FriendlyByteBuf p_320402_) {
        p_320402_.writeResourceLocation(this.key);
    }

    @Override
    public PacketType<ClientboundCookieRequestPacket> type() {
        return CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST;
    }

    public void handle(ClientCookiePacketListener p_320203_) {
        p_320203_.handleRequestCookie(this);
    }
}
