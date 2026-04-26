package net.minecraft.util;

import java.util.Locale;
import java.util.function.Consumer;

public class StaticCache2D<T> {
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final Object[] cache;

    public static <T> StaticCache2D<T> create(int p_347579_, int p_347687_, int p_347693_, StaticCache2D.Initializer<T> p_347478_) {
        int i = p_347579_ - p_347693_;
        int j = p_347687_ - p_347693_;
        int k = 2 * p_347693_ + 1;
        return new StaticCache2D<>(i, j, k, k, p_347478_);
    }

    private StaticCache2D(int p_347480_, int p_347568_, int p_347475_, int p_347530_, StaticCache2D.Initializer<T> p_347453_) {
        this.minX = p_347480_;
        this.minZ = p_347568_;
        this.sizeX = p_347475_;
        this.sizeZ = p_347530_;
        this.cache = new Object[this.sizeX * this.sizeZ];

        for (int i = p_347480_; i < p_347480_ + p_347475_; i++) {
            for (int j = p_347568_; j < p_347568_ + p_347530_; j++) {
                this.cache[this.getIndex(i, j)] = p_347453_.get(i, j);
            }
        }
    }

    public void forEach(Consumer<T> p_347572_) {
        for (Object object : this.cache) {
            p_347572_.accept((T)object);
        }
    }

    public T get(int p_347699_, int p_347563_) {
        if (!this.contains(p_347699_, p_347563_)) {
            throw new IllegalArgumentException("Requested out of range value (" + p_347699_ + "," + p_347563_ + ") from " + this);
        } else {
            return (T)this.cache[this.getIndex(p_347699_, p_347563_)];
        }
    }

    public boolean contains(int p_347591_, int p_347645_) {
        int i = p_347591_ - this.minX;
        int j = p_347645_ - this.minZ;
        return i >= 0 && i < this.sizeX && j >= 0 && j < this.sizeZ;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "StaticCache2D[%d, %d, %d, %d]", this.minX, this.minZ, this.minX + this.sizeX, this.minZ + this.sizeZ);
    }

    private int getIndex(int p_347703_, int p_347664_) {
        int i = p_347703_ - this.minX;
        int j = p_347664_ - this.minZ;
        return i * this.sizeZ + j;
    }

    @FunctionalInterface
    public interface Initializer<T> {
        T get(int p_347711_, int p_347489_);
    }
}
