package net.minecraft.util.debugchart;

public interface SampleStorage {
    int capacity();

    int size();

    long get(int p_323594_);

    long get(int p_324468_, int p_324624_);

    void reset();
}
