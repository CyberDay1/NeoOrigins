package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.VisibleForDebug;

public interface ProtocolInfo<T extends PacketListener> {
    ConnectionProtocol id();

    PacketFlow flow();

    StreamCodec<ByteBuf, Packet<? super T>> codec();

    @Nullable
    BundlerInfo bundlerInfo();

    public interface Unbound<T extends PacketListener, B extends ByteBuf> {
        ProtocolInfo<T> bind(Function<ByteBuf, B> p_319787_);

        ConnectionProtocol id();

        PacketFlow flow();

        @VisibleForDebug
        void listPackets(ProtocolInfo.Unbound.PacketVisitor p_352215_);

        @FunctionalInterface
        public interface PacketVisitor {
            void accept(PacketType<?> p_352070_, int p_352333_);
        }
    }
}
