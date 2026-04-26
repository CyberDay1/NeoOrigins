package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;

public class ProtocolCodecBuilder<B extends ByteBuf, L extends PacketListener> {
    private final IdDispatchCodec.Builder<B, Packet<? super L>, PacketType<? extends Packet<? super L>>> dispatchBuilder = IdDispatchCodec.builder(Packet::type);
    private final PacketFlow flow;

    public ProtocolCodecBuilder(PacketFlow p_320925_) {
        this.flow = p_320925_;
    }

    public <T extends Packet<? super L>> ProtocolCodecBuilder<B, L> add(PacketType<T> p_319852_, StreamCodec<? super B, T> p_320657_) {
        if (p_319852_.flow() != this.flow) {
            throw new IllegalArgumentException("Invalid packet flow for packet " + p_319852_ + ", expected " + this.flow.name());
        } else {
            this.dispatchBuilder.add(p_319852_, p_320657_);
            return this;
        }
    }

    public StreamCodec<B, Packet<? super L>> build() {
        return this.dispatchBuilder.build();
    }
}
