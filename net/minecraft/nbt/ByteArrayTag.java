package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class ByteArrayTag extends CollectionTag<ByteTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<ByteArrayTag> TYPE = new TagType.VariableSize<ByteArrayTag>() {
        public ByteArrayTag load(DataInput p_128252_, NbtAccounter p_128254_) throws IOException {
            return new ByteArrayTag(readAccounted(p_128252_, p_128254_));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput p_197433_, StreamTagVisitor p_197434_, NbtAccounter p_302366_) throws IOException {
            return p_197434_.visit(readAccounted(p_197433_, p_302366_));
        }

        private static byte[] readAccounted(DataInput p_302389_, NbtAccounter p_302320_) throws IOException {
            p_302320_.accountBytes(24L);
            int i = p_302389_.readInt();
            p_302320_.accountBytes(1L, (long)i);
            byte[] abyte = new byte[i];
            p_302389_.readFully(abyte);
            return abyte;
        }

        @Override
        public void skip(DataInput p_197431_, NbtAccounter p_302351_) throws IOException {
            p_197431_.skipBytes(p_197431_.readInt() * 1);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public ByteArrayTag(byte[] p_128191_) {
        this.data = p_128191_;
    }

    public ByteArrayTag(List<Byte> p_128189_) {
        this(toArray(p_128189_));
    }

    private static byte[] toArray(List<Byte> p_128207_) {
        byte[] abyte = new byte[p_128207_.size()];

        for (int i = 0; i < p_128207_.size(); i++) {
            Byte obyte = p_128207_.get(i);
            abyte[i] = obyte == null ? 0 : obyte;
        }

        return abyte;
    }

    @Override
    public void write(DataOutput p_128202_) throws IOException {
        p_128202_.writeInt(this.data.length);
        p_128202_.write(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 24 + 1 * this.data.length;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public TagType<ByteArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    @Override
    public Tag copy() {
        byte[] abyte = new byte[this.data.length];
        System.arraycopy(this.data, 0, abyte, 0, this.data.length);
        return new ByteArrayTag(abyte);
    }

    @Override
    public boolean equals(Object p_128233_) {
        return this == p_128233_ ? true : p_128233_ instanceof ByteArrayTag && Arrays.equals(this.data, ((ByteArrayTag)p_128233_).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor p_177839_) {
        p_177839_.visitByteArray(this);
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    public ByteTag get(int p_128194_) {
        return ByteTag.valueOf(this.data[p_128194_]);
    }

    public ByteTag set(int p_128196_, ByteTag p_128197_) {
        byte b0 = this.data[p_128196_];
        this.data[p_128196_] = p_128197_.getAsByte();
        return ByteTag.valueOf(b0);
    }

    public void add(int p_128215_, ByteTag p_128216_) {
        this.data = ArrayUtils.add(this.data, p_128215_, p_128216_.getAsByte());
    }

    @Override
    public boolean setTag(int p_128199_, Tag p_128200_) {
        if (p_128200_ instanceof NumericTag) {
            this.data[p_128199_] = ((NumericTag)p_128200_).getAsByte();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int p_128218_, Tag p_128219_) {
        if (p_128219_ instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, p_128218_, ((NumericTag)p_128219_).getAsByte());
            return true;
        } else {
            return false;
        }
    }

    public ByteTag remove(int p_128213_) {
        byte b0 = this.data[p_128213_];
        this.data = ArrayUtils.remove(this.data, p_128213_);
        return ByteTag.valueOf(b0);
    }

    @Override
    public byte getElementType() {
        return 1;
    }

    @Override
    public void clear() {
        this.data = new byte[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor p_197429_) {
        return p_197429_.visit(this.data);
    }
}
