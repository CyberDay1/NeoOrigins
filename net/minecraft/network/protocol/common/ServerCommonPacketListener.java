package net.minecraft.network.protocol.common;

import net.minecraft.network.protocol.cookie.ServerCookiePacketListener;
import net.minecraft.network.protocol.game.ServerPacketListener;

public interface ServerCommonPacketListener extends ServerCookiePacketListener, ServerPacketListener, net.neoforged.neoforge.common.extensions.IServerCommonPacketListenerExtension {
    void handleKeepAlive(ServerboundKeepAlivePacket p_296457_);

    void handlePong(ServerboundPongPacket p_294309_);

    void handleCustomPayload(ServerboundCustomPayloadPacket p_295175_);

    void handleResourcePackResponse(ServerboundResourcePackPacket p_294545_);

    void handleClientInformation(ServerboundClientInformationPacket p_301982_);
}
