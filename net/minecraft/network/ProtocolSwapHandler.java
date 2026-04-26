package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.Packet;

public interface ProtocolSwapHandler {
    static void handleInboundTerminalPacket(ChannelHandlerContext p_320154_, Packet<?> p_320949_) {
        if (p_320949_.isTerminal()) {
            p_320154_.channel().config().setAutoRead(false);
            p_320154_.pipeline().addBefore(p_320154_.name(), "inbound_config", new UnconfiguredPipelineHandler.Inbound());
            p_320154_.pipeline().remove(p_320154_.name());
        }
    }

    static void handleOutboundTerminalPacket(ChannelHandlerContext p_320395_, Packet<?> p_320209_) {
        if (p_320209_.isTerminal()) {
            p_320395_.pipeline().addAfter(p_320395_.name(), "outbound_config", new UnconfiguredPipelineHandler.Outbound());
            p_320395_.pipeline().remove(p_320395_.name());
        }
    }
}
