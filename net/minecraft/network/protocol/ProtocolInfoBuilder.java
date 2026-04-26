package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.codec.StreamCodec;

public class ProtocolInfoBuilder<T extends PacketListener, B extends ByteBuf> {
    final ConnectionProtocol protocol;
    final PacketFlow flow;
    private final List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> codecs = new ArrayList<>();
    @Nullable
    private BundlerInfo bundlerInfo;

    public ProtocolInfoBuilder(ConnectionProtocol p_320213_, PacketFlow p_320424_) {
        this.protocol = p_320213_;
        this.flow = p_320424_;
    }

    public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B> addPacket(PacketType<P> p_320673_, StreamCodec<? super B, P> p_319828_) {
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(p_320673_, p_319828_));
        return this;
    }

    public <P extends BundlePacket<? super T>, D extends BundleDelimiterPacket<? super T>> ProtocolInfoBuilder<T, B> withBundlePacket(
        PacketType<P> p_320954_, Function<Iterable<Packet<? super T>>, P> p_320241_, D p_320202_
    ) {
        StreamCodec<ByteBuf, D> streamcodec = StreamCodec.unit(p_320202_);
        PacketType<D> packettype = (PacketType<D>)(PacketType<?>)p_320202_.type();
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packettype, streamcodec));
        this.bundlerInfo = BundlerInfo.createForPacket(p_320954_, p_320241_, p_320202_);
        return this;
    }

    StreamCodec<ByteBuf, Packet<? super T>> buildPacketCodec(Function<ByteBuf, B> p_320922_, List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> p_320733_) {
        ProtocolCodecBuilder<ByteBuf, T> protocolcodecbuilder = new ProtocolCodecBuilder<>(this.flow);

        for (ProtocolInfoBuilder.CodecEntry<T, ?, B> codecentry : p_320733_) {
            codecentry.addToBuilder(protocolcodecbuilder, p_320922_);
        }

        return protocolcodecbuilder.build();
    }

    public ProtocolInfo<T> build(Function<ByteBuf, B> p_319806_) {
        return new ProtocolInfoBuilder.Implementation<>(this.protocol, this.flow, this.buildPacketCodec(p_319806_, this.codecs), this.bundlerInfo);
    }

    public ProtocolInfo.Unbound<T, B> buildUnbound() {
        final List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> list = List.copyOf(this.codecs);
        final BundlerInfo bundlerinfo = this.bundlerInfo;
        return new ProtocolInfo.Unbound<T, B>() {
            @Override
            public ProtocolInfo<T> bind(Function<ByteBuf, B> p_352173_) {
                return new ProtocolInfoBuilder.Implementation<>(
                    ProtocolInfoBuilder.this.protocol, ProtocolInfoBuilder.this.flow, ProtocolInfoBuilder.this.buildPacketCodec(p_352173_, list), bundlerinfo
                );
            }

            @Override
            public ConnectionProtocol id() {
                return ProtocolInfoBuilder.this.protocol;
            }

            @Override
            public PacketFlow flow() {
                return ProtocolInfoBuilder.this.flow;
            }

            @Override
            public void listPackets(ProtocolInfo.Unbound.PacketVisitor p_352332_) {
                for (int i = 0; i < list.size(); i++) {
                    ProtocolInfoBuilder.CodecEntry<T, ?, B> codecentry = list.get(i);
                    p_352332_.accept(codecentry.type, i);
                }
            }
        };
    }

    private static <L extends PacketListener, B extends ByteBuf> ProtocolInfo.Unbound<L, B> protocol(
        ConnectionProtocol p_320849_, PacketFlow p_320146_, Consumer<ProtocolInfoBuilder<L, B>> p_320140_
    ) {
        ProtocolInfoBuilder<L, B> protocolinfobuilder = new ProtocolInfoBuilder<>(p_320849_, p_320146_);
        p_320140_.accept(protocolinfobuilder);
        return protocolinfobuilder.buildUnbound();
    }

    public static <T extends ServerboundPacketListener, B extends ByteBuf> ProtocolInfo.Unbound<T, B> serverboundProtocol(
        ConnectionProtocol p_319767_, Consumer<ProtocolInfoBuilder<T, B>> p_320799_
    ) {
        return protocol(p_319767_, PacketFlow.SERVERBOUND, p_320799_);
    }

    public static <T extends ClientboundPacketListener, B extends ByteBuf> ProtocolInfo.Unbound<T, B> clientboundProtocol(
        ConnectionProtocol p_320428_, Consumer<ProtocolInfoBuilder<T, B>> p_320292_
    ) {
        return protocol(p_320428_, PacketFlow.CLIENTBOUND, p_320292_);
    }

    static record CodecEntry<T extends PacketListener, P extends Packet<? super T>, B extends ByteBuf>(PacketType<P> type, StreamCodec<? super B, P> serializer) {
        public void addToBuilder(ProtocolCodecBuilder<ByteBuf, T> p_320857_, Function<ByteBuf, B> p_320646_) {
            StreamCodec<ByteBuf, P> streamcodec = this.serializer.mapStream(p_320646_);
            p_320857_.add(this.type, streamcodec);
        }
    }

    static record Implementation<L extends PacketListener>(
        ConnectionProtocol id, PacketFlow flow, StreamCodec<ByteBuf, Packet<? super L>> codec, @Nullable BundlerInfo bundlerInfo
    ) implements ProtocolInfo<L> {
    }
}
