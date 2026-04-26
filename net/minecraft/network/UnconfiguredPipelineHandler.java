package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;

public class UnconfiguredPipelineHandler {
    public static <T extends PacketListener> UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundProtocol(ProtocolInfo<T> p_320435_) {
        return setupInboundHandler(new PacketDecoder<>(p_320435_));
    }

    private static UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundHandler(ChannelInboundHandler p_320340_) {
        return p_320663_ -> {
            p_320663_.pipeline().replace(p_320663_.name(), "decoder", p_320340_);
            p_320663_.channel().config().setAutoRead(true);
        };
    }

    public static <T extends PacketListener> UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundProtocol(ProtocolInfo<T> p_320204_) {
        return setupOutboundHandler(new PacketEncoder<>(p_320204_));
    }

    private static UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundHandler(ChannelOutboundHandler p_319887_) {
        return p_320755_ -> p_320755_.pipeline().replace(p_320755_.name(), "encoder", p_319887_);
    }

    public static class Inbound extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext p_319789_, Object p_320107_) {
            if (!(p_320107_ instanceof ByteBuf) && !(p_320107_ instanceof Packet)) {
                p_319789_.fireChannelRead(p_320107_);
            } else {
                ReferenceCountUtil.release(p_320107_);
                throw new DecoderException("Pipeline has no inbound protocol configured, can't process packet " + p_320107_);
            }
        }

        @Override
        public void write(ChannelHandlerContext p_320891_, Object p_320121_, ChannelPromise p_320677_) throws Exception {
            if (p_320121_ instanceof UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler$inboundconfigurationtask) {
                try {
                    unconfiguredpipelinehandler$inboundconfigurationtask.run(p_320891_);
                } finally {
                    ReferenceCountUtil.release(p_320121_);
                }

                p_320677_.setSuccess();
            } else {
                p_320891_.write(p_320121_, p_320677_);
            }
        }
    }

    @FunctionalInterface
    public interface InboundConfigurationTask {
        void run(ChannelHandlerContext p_320322_);

        default UnconfiguredPipelineHandler.InboundConfigurationTask andThen(UnconfiguredPipelineHandler.InboundConfigurationTask p_320407_) {
            return p_320785_ -> {
                this.run(p_320785_);
                p_320407_.run(p_320785_);
            };
        }
    }

    public static class Outbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext p_320511_, Object p_319817_, ChannelPromise p_320083_) throws Exception {
            if (p_319817_ instanceof Packet) {
                ReferenceCountUtil.release(p_319817_);
                throw new EncoderException("Pipeline has no outbound protocol configured, can't process packet " + p_319817_);
            } else {
                if (p_319817_ instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler$outboundconfigurationtask) {
                    try {
                        unconfiguredpipelinehandler$outboundconfigurationtask.run(p_320511_);
                    } finally {
                        ReferenceCountUtil.release(p_319817_);
                    }

                    p_320083_.setSuccess();
                } else {
                    p_320511_.write(p_319817_, p_320083_);
                }
            }
        }
    }

    @FunctionalInterface
    public interface OutboundConfigurationTask {
        void run(ChannelHandlerContext p_320186_);

        default UnconfiguredPipelineHandler.OutboundConfigurationTask andThen(UnconfiguredPipelineHandler.OutboundConfigurationTask p_320148_) {
            return p_320612_ -> {
                this.run(p_320612_);
                p_320148_.run(p_320612_);
            };
        }
    }
}
