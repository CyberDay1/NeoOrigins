package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import javax.annotation.Nullable;

public class Varint21FrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_VARINT21_BYTES = 3;
    private final ByteBuf helperBuf = Unpooled.directBuffer(3);
    @Nullable
    private final BandwidthDebugMonitor monitor;

    public Varint21FrameDecoder(@Nullable BandwidthDebugMonitor p_298640_) {
        this.monitor = p_298640_;
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext p_294580_) {
        this.helperBuf.release();
    }

    private static boolean copyVarint(ByteBuf p_294780_, ByteBuf p_294299_) {
        for (int i = 0; i < 3; i++) {
            if (!p_294780_.isReadable()) {
                return false;
            }

            byte b0 = p_294780_.readByte();
            p_294299_.writeByte(b0);
            if (!VarInt.hasContinuationBit(b0)) {
                return true;
            }
        }

        throw new CorruptedFrameException("length wider than 21-bit");
    }

    @Override
    protected void decode(ChannelHandlerContext p_130566_, ByteBuf p_130567_, List<Object> p_130568_) {
        p_130567_.markReaderIndex();
        this.helperBuf.clear();
        if (!copyVarint(p_130567_, this.helperBuf)) {
            p_130567_.resetReaderIndex();
        } else {
            int i = VarInt.read(this.helperBuf);
            if (p_130567_.readableBytes() < i) {
                p_130567_.resetReaderIndex();
            } else {
                if (this.monitor != null) {
                    this.monitor.onReceive(i + VarInt.getByteSize(i));
                }

                p_130568_.add(p_130567_.readBytes(i));
            }
        }
    }
}
