package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class MonitorFrameDecoder extends ChannelInboundHandlerAdapter {
    private final BandwidthDebugMonitor monitor;

    public MonitorFrameDecoder(BandwidthDebugMonitor p_341653_) {
        this.monitor = p_341653_;
    }

    @Override
    public void channelRead(ChannelHandlerContext p_341698_, Object p_341594_) {
        if (p_341594_ instanceof ByteBuf bytebuf) {
            this.monitor.onReceive(bytebuf.readableBytes());
        }

        p_341698_.fireChannelRead(p_341594_);
    }
}
