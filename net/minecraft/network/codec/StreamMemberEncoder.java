package net.minecraft.network.codec;

@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T p_320162_, O p_320695_);
}
