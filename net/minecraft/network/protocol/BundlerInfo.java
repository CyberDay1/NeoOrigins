package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.PacketListener;

public interface BundlerInfo {
    int BUNDLE_SIZE_LIMIT = 4096;

    static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
        final PacketType<P> p_320816_, final Function<Iterable<Packet<? super T>>, P> p_265627_, final BundleDelimiterPacket<? super T> p_265373_
    ) {
        return new BundlerInfo() {
            @Override
            public void unbundlePacket(Packet<?> p_265538_, Consumer<Packet<?>> p_265064_) {
                if (p_265538_.type() == p_320816_) {
                    P p = (P)p_265538_;
                    p_265064_.accept(p_265373_);
                    p.subPackets().forEach(p_265064_);
                    p_265064_.accept(p_265373_);
                } else {
                    p_265064_.accept(p_265538_);
                }
            }

            @Override
            public void unbundlePacket(Packet<?> bundlePacket, Consumer<Packet<?>> packetSender, io.netty.channel.ChannelHandlerContext context) {
                if (bundlePacket.type() == p_320816_) {
                    P p = (P)bundlePacket;
                    java.util.List<Packet<?>> packets = net.neoforged.neoforge.network.registration.NetworkRegistry.filterGameBundlePackets(context, p.subPackets());
                    if (packets.isEmpty()) {
                        return;
                    }
                    if (packets.size() == 1) {
                        packetSender.accept(packets.get(0));
                        return;
                    }
                    packetSender.accept(p_265373_);
                    packets.forEach(packetSender);
                    packetSender.accept(p_265373_);
                } else {
                    packetSender.accept(bundlePacket);
                }
            }

            @Nullable
            @Override
            public BundlerInfo.Bundler startPacketBundling(Packet<?> p_265749_) {
                return p_265749_ == p_265373_ ? new BundlerInfo.Bundler() {
                    private final List<Packet<? super T>> bundlePackets = new ArrayList<>();

                    @Nullable
                    @Override
                    public Packet<?> addPacket(Packet<?> p_320276_) {
                        if (p_320276_ == p_265373_) {
                            return p_265627_.apply(this.bundlePackets);
                        } else if (this.bundlePackets.size() >= 4096) {
                            throw new IllegalStateException("Too many packets in a bundle");
                        } else {
                            this.bundlePackets.add((Packet<? super T>)p_320276_);
                            return null;
                        }
                    }
                } : null;
            }
        };
    }

    /**
     * @deprecated Use {@link #unbundlePacket(Packet, Consumer, io.netty.channel.ChannelHandlerContext)} instead, as it supports packet filtering and is more efficient.
     */
    @Deprecated
    void unbundlePacket(Packet<?> p_265095_, Consumer<Packet<?>> p_265715_);

    /**
     * Unwrap and flattens a bundle packet.
     * Then sends the packets contained in the bundle, bracketing them in delimiter packets if need be.
     *
     * @param bundlePacket The bundle packet to write.
     * @param packetSender The packet sender.
     * @param context The network context.
     * @implNote This implementation should filter out packets which are not sendable on the current context, however to preserve compatibility the default implementation does not do this.
     */
    default void unbundlePacket(Packet<?> bundlePacket, Consumer<Packet<?>> packetSender, io.netty.channel.ChannelHandlerContext context) {
        unbundlePacket(bundlePacket, packetSender);
    }

    @Nullable
    BundlerInfo.Bundler startPacketBundling(Packet<?> p_265162_);

    public interface Bundler {
        @Nullable
        Packet<?> addPacket(Packet<?> p_265601_);
    }
}
