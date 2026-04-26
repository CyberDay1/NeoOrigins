package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class VarLong {
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public static int getByteSize(long p_295931_) {
        for (int i = 1; i < 10; i++) {
            if ((p_295931_ & -1L << i * 7) == 0L) {
                return i;
            }
        }

        return 10;
    }

    public static boolean hasContinuationBit(byte p_294159_) {
        return (p_294159_ & 128) == 128;
    }

    public static long read(ByteBuf p_294443_) {
        long i = 0L;
        int j = 0;

        byte b0;
        do {
            b0 = p_294443_.readByte();
            i |= (long)(b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while (hasContinuationBit(b0));

        return i;
    }

    public static ByteBuf write(ByteBuf p_296144_, long p_295377_) {
        while ((p_295377_ & -128L) != 0L) {
            p_296144_.writeByte((int)(p_295377_ & 127L) | 128);
            p_295377_ >>>= 7;
        }

        p_296144_.writeByte((int)p_295377_);
        return p_296144_;
    }
}
