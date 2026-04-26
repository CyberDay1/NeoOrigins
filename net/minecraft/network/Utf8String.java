package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;

public class Utf8String {
    public static String read(ByteBuf p_295677_, int p_295190_) {
        int i = ByteBufUtil.utf8MaxBytes(p_295190_);
        int j = VarInt.read(p_295677_);
        if (j > i) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + j + " > " + i + ")");
        } else if (j < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            int k = p_295677_.readableBytes();
            if (j > k) {
                throw new DecoderException("Not enough bytes in buffer, expected " + j + ", but got " + k);
            } else {
                String s = p_295677_.toString(p_295677_.readerIndex(), j, StandardCharsets.UTF_8);
                p_295677_.readerIndex(p_295677_.readerIndex() + j);
                if (s.length() > p_295190_) {
                    throw new DecoderException("The received string length is longer than maximum allowed (" + s.length() + " > " + p_295190_ + ")");
                } else {
                    return s;
                }
            }
        }
    }

    public static void write(ByteBuf p_295811_, CharSequence p_295702_, int p_295310_) {
        if (p_295702_.length() > p_295310_) {
            throw new EncoderException("String too big (was " + p_295702_.length() + " characters, max " + p_295310_ + ")");
        } else {
            int i = ByteBufUtil.utf8MaxBytes(p_295702_);
            ByteBuf bytebuf = p_295811_.alloc().buffer(i);

            try {
                int j = ByteBufUtil.writeUtf8(bytebuf, p_295702_);
                int k = ByteBufUtil.utf8MaxBytes(p_295310_);
                if (j > k) {
                    throw new EncoderException("String too big (was " + j + " bytes encoded, max " + k + ")");
                }

                VarInt.write(p_295811_, j);
                p_295811_.writeBytes(bytebuf);
            } finally {
                bytebuf.release();
            }
        }
    }
}
