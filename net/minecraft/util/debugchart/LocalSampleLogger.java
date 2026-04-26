package net.minecraft.util.debugchart;

public class LocalSampleLogger extends AbstractSampleLogger implements SampleStorage {
    public static final int CAPACITY = 240;
    private final long[][] samples;
    private int start;
    private int size;

    public LocalSampleLogger(int p_324172_) {
        this(p_324172_, new long[p_324172_]);
    }

    public LocalSampleLogger(int p_323703_, long[] p_324179_) {
        super(p_323703_, p_324179_);
        this.samples = new long[240][p_323703_];
    }

    @Override
    protected void useSample() {
        int i = this.wrapIndex(this.start + this.size);
        System.arraycopy(this.sample, 0, this.samples[i], 0, this.sample.length);
        if (this.size < 240) {
            this.size++;
        } else {
            this.start = this.wrapIndex(this.start + 1);
        }
    }

    @Override
    public int capacity() {
        return this.samples.length;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public long get(int p_324212_) {
        return this.get(p_324212_, 0);
    }

    @Override
    public long get(int p_323840_, int p_323620_) {
        if (p_323840_ >= 0 && p_323840_ < this.size) {
            long[] along = this.samples[this.wrapIndex(this.start + p_323840_)];
            if (p_323620_ >= 0 && p_323620_ < along.length) {
                return along[p_323620_];
            } else {
                throw new IndexOutOfBoundsException(p_323620_ + " out of bounds for dimensions " + along.length);
            }
        } else {
            throw new IndexOutOfBoundsException(p_323840_ + " out of bounds for length " + this.size);
        }
    }

    private int wrapIndex(int p_324200_) {
        return p_324200_ % 240;
    }

    @Override
    public void reset() {
        this.start = 0;
        this.size = 0;
    }
}
