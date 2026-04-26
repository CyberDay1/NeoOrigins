package net.minecraft.util.debugchart;

public abstract class AbstractSampleLogger implements SampleLogger {
    protected final long[] defaults;
    protected final long[] sample;

    protected AbstractSampleLogger(int p_323598_, long[] p_323892_) {
        if (p_323892_.length != p_323598_) {
            throw new IllegalArgumentException("defaults have incorrect length of " + p_323892_.length);
        } else {
            this.sample = new long[p_323598_];
            this.defaults = p_323892_;
        }
    }

    @Override
    public void logFullSample(long[] p_324158_) {
        System.arraycopy(p_324158_, 0, this.sample, 0, p_324158_.length);
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logSample(long p_324223_) {
        this.sample[0] = p_324223_;
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logPartialSample(long p_323475_, int p_324235_) {
        if (p_324235_ >= 1 && p_324235_ < this.sample.length) {
            this.sample[p_324235_] = p_323475_;
        } else {
            throw new IndexOutOfBoundsException(p_324235_ + " out of bounds for dimensions " + this.sample.length);
        }
    }

    protected abstract void useSample();

    protected void resetSample() {
        System.arraycopy(this.defaults, 0, this.sample, 0, this.defaults.length);
    }
}
