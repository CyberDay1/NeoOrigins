package net.minecraft.network.codec;

@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O p_320158_, T p_320396_);
}
