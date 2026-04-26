package net.minecraft.network.protocol.cookie;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class CookiePacketTypes {
    public static final PacketType<ClientboundCookieRequestPacket> CLIENTBOUND_COOKIE_REQUEST = createClientbound("cookie_request");
    public static final PacketType<ServerboundCookieResponsePacket> SERVERBOUND_COOKIE_RESPONSE = createServerbound("cookie_response");

    private static <T extends Packet<ClientCookiePacketListener>> PacketType<T> createClientbound(String p_320264_) {
        return new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.withDefaultNamespace(p_320264_));
    }

    private static <T extends Packet<ServerCookiePacketListener>> PacketType<T> createServerbound(String p_320414_) {
        return new PacketType<>(PacketFlow.SERVERBOUND, ResourceLocation.withDefaultNamespace(p_320414_));
    }
}
