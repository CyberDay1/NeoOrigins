package net.minecraft.network.protocol.login;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.cookie.ServerCookiePacketListener;
import net.minecraft.network.protocol.game.ServerPacketListener;

public interface ServerLoginPacketListener extends ServerCookiePacketListener, ServerPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.LOGIN;
    }

    void handleHello(ServerboundHelloPacket p_134823_);

    void handleKey(ServerboundKeyPacket p_134824_);

    void handleCustomQueryPacket(ServerboundCustomQueryAnswerPacket p_296280_);

    void handleLoginAcknowledgement(ServerboundLoginAcknowledgedPacket p_294889_);
}
