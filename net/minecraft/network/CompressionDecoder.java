package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class CompressionDecoder extends ByteToMessageDecoder {
    public static final int MAXIMUM_COMPRESSED_LENGTH = 2097152;
    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8388608;
    private final Inflater inflater;
    private int threshold;
    private boolean validateDecompressed;

    public CompressionDecoder(int p_182675_, boolean p_182676_) {
        this.threshold = p_182675_;
        this.validateDecompressed = p_182676_;
        this.inflater = new Inflater();
    }

    @Override
    protected void decode(ChannelHandlerContext p_129441_, ByteBuf p_129442_, List<Object> p_129443_) throws Exception {
        if (p_129442_.readableBytes() != 0) {
            int i = VarInt.read(p_129442_);
            if (i == 0) {
                p_129443_.add(p_129442_.readBytes(p_129442_.readableBytes()));
            } else {
                if (this.validateDecompressed) {
                    if (i < this.threshold) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is below server threshold of " + this.threshold);
                    }

                    if (i > 8388608) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is larger than protocol maximum of 8388608");
                    }
                }

                this.setupInflaterInput(p_129442_);
                ByteBuf bytebuf = this.inflate(p_129441_, i);
                this.inflater.reset();
                p_129443_.add(bytebuf);
            }
        }
    }

    private void setupInflaterInput(ByteBuf p_296004_) {
        ByteBuffer bytebuffer;
        if (p_296004_.nioBufferCount() > 0) {
            bytebuffer = p_296004_.nioBuffer();
            p_296004_.skipBytes(p_296004_.readableBytes());
        } else {
            bytebuffer = ByteBuffer.allocateDirect(p_296004_.readableBytes());
            p_296004_.readBytes(bytebuffer);
            bytebuffer.flip();
        }

        this.inflater.setInput(bytebuffer);
    }

    private ByteBuf inflate(ChannelHandlerContext p_295791_, int p_295281_) throws DataFormatException {
        ByteBuf bytebuf = p_295791_.alloc().directBuffer(p_295281_);

        try {
            ByteBuffer bytebuffer = bytebuf.internalNioBuffer(0, p_295281_);
            int i = bytebuffer.position();
            this.inflater.inflate(bytebuffer);
            int j = bytebuffer.position() - i;
            if (j != p_295281_) {
                throw new DecoderException(
                    "Badly compressed packet - actual length of uncompressed payload " + j + " is does not match declared size " + p_295281_
                );
            } else {
                bytebuf.writerIndex(bytebuf.writerIndex() + j);
                return bytebuf;
            }
        } catch (Exception exception) {
            bytebuf.release();
            throw exception;
        }
    }

    public void setThreshold(int p_182678_, boolean p_182679_) {
        this.threshold = p_182678_;
        this.validateDecompressed = p_182679_;
    }
}
