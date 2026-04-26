package net.minecraft.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {
    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), p_202569_ -> p_202569_.add(ROOT_MARKER));
    public static final Marker PACKET_RECEIVED_MARKER = Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), p_202562_ -> p_202562_.add(PACKET_MARKER));
    public static final Marker PACKET_SENT_MARKER = Util.make(MarkerFactory.getMarker("PACKET_SENT"), p_202557_ -> p_202557_.add(PACKET_MARKER));
    public static final Supplier<NioEventLoopGroup> NETWORK_WORKER_GROUP = Suppliers.memoize(
        () -> new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Client IO #%d").setDaemon(true).build())
    );
    public static final Supplier<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = Suppliers.memoize(
        () -> new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build())
    );
    public static final Supplier<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = Suppliers.memoize(
        () -> new DefaultEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Local Client IO #%d").setDaemon(true).build())
    );
    private static final ProtocolInfo<ServerHandshakePacketListener> INITIAL_PROTOCOL = HandshakeProtocols.SERVERBOUND;
    private final PacketFlow receiving;
    private volatile boolean sendLoginDisconnect = true;
    private final Queue<Consumer<Connection>> pendingActions = Queues.newConcurrentLinkedQueue();
    private Channel channel;
    private SocketAddress address;
    @Nullable
    private volatile PacketListener disconnectListener;
    @Nullable
    private volatile PacketListener packetListener;
    @Nullable
    private DisconnectionDetails disconnectionDetails;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    @Nullable
    private volatile DisconnectionDetails delayedDisconnect;
    @Nullable
    BandwidthDebugMonitor bandwidthDebugMonitor;
    @Nullable
    private ProtocolInfo<?> inboundProtocol;

    public Connection(PacketFlow p_129482_) {
        this.receiving = p_129482_;
    }

    @Override
    public void channelActive(ChannelHandlerContext p_129525_) throws Exception {
        super.channelActive(p_129525_);
        this.channel = p_129525_.channel();
        this.address = this.channel.remoteAddress();
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }
        net.neoforged.neoforge.network.connection.ConnectionUtils.setConnection(p_129525_, this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext p_129527_) {
        this.disconnect(Component.translatable("disconnect.endOfStream"));
        net.neoforged.neoforge.network.connection.ConnectionUtils.removeConnection(p_129527_);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext p_129533_, Throwable p_129534_) {
        if (p_129534_ instanceof SkipPacketException) {
            LOGGER.debug("Skipping packet due to errors", p_129534_.getCause());
        } else {
            boolean flag = !this.handlingFault;
            this.handlingFault = true;
            if (this.channel.isOpen()) {
                if (p_129534_ instanceof TimeoutException) {
                    LOGGER.debug("Timeout", p_129534_);
                    this.disconnect(Component.translatable("disconnect.timeout"));
                } else {
                    Component component = Component.translatable("disconnect.genericReason", "Internal Exception: " + p_129534_);
                    PacketListener packetlistener = this.packetListener;
                    if (packetlistener != null) {
                        ConnectionProtocol protocol = packetlistener.protocol();
                        if (protocol == ConnectionProtocol.CONFIGURATION || protocol == ConnectionProtocol.PLAY) {
                            // Neo: Always log critical network exceptions for config and play packets
                            LOGGER.error("Exception caught in connection", p_129534_);
                        }
                    }
                    DisconnectionDetails disconnectiondetails;
                    if (packetlistener != null) {
                        disconnectiondetails = packetlistener.createDisconnectionInfo(component, p_129534_);
                    } else {
                        disconnectiondetails = new DisconnectionDetails(component);
                    }

                    if (flag) {
                        LOGGER.debug("Failed to sent packet", p_129534_);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            Packet<?> packet = (Packet<?>)(this.sendLoginDisconnect
                                ? new ClientboundLoginDisconnectPacket(component)
                                : new ClientboundDisconnectPacket(component));
                            this.send(packet, PacketSendListener.thenRun(() -> this.disconnect(disconnectiondetails)));
                        } else {
                            this.disconnect(disconnectiondetails);
                        }

                        this.setReadOnly();
                    } else {
                        LOGGER.debug("Double fault", p_129534_);
                        this.disconnect(disconnectiondetails);
                    }
                }
            }
        }
    }

    protected void channelRead0(ChannelHandlerContext p_129487_, Packet<?> p_129488_) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;
            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                if (packetlistener.shouldHandleMessage(p_129488_)) {
                    try {
                        genericsFtw(p_129488_, packetlistener);
                    } catch (RunningOnDifferentThreadException runningondifferentthreadexception) {
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        LOGGER.error("Received {} that couldn't be processed", p_129488_.getClass(), classcastexception);
                        this.disconnect(Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    this.receivedPackets++;
                }
            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> p_129518_, PacketListener p_129519_) {
        p_129518_.handle((T)p_129519_);
    }

    private void validateListener(ProtocolInfo<?> p_320630_, PacketListener p_320459_) {
        Validate.notNull(p_320459_, "packetListener");
        PacketFlow packetflow = p_320459_.flow();
        if (packetflow != this.receiving) {
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + packetflow);
        } else {
            ConnectionProtocol connectionprotocol = p_320459_.protocol();
            if (p_320630_.id() != connectionprotocol) {
                throw new IllegalStateException("Listener protocol (" + connectionprotocol + ") does not match requested one " + p_320630_);
            }
        }
    }

    private static void syncAfterConfigurationChange(ChannelFuture p_341671_) {
        try {
            p_341671_.syncUninterruptibly();
        } catch (Exception exception) {
            if (exception instanceof ClosedChannelException) {
                LOGGER.info("Connection closed during protocol change");
            } else {
                throw exception;
            }
        }
    }

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> p_320903_, T p_320940_) {
        this.validateListener(p_320903_, p_320940_);
        if (p_320903_.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + p_320903_.id());
        } else {
            this.inboundProtocol = p_320903_;
            this.packetListener = p_320940_;
            this.disconnectListener = null;
            UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler$inboundconfigurationtask = UnconfiguredPipelineHandler.setupInboundProtocol(
                p_320903_
            );
            BundlerInfo bundlerinfo = p_320903_.bundlerInfo();
            if (bundlerinfo != null) {
                PacketBundlePacker packetbundlepacker = new PacketBundlePacker(bundlerinfo);
                unconfiguredpipelinehandler$inboundconfigurationtask = unconfiguredpipelinehandler$inboundconfigurationtask.andThen(
                    p_319518_ -> p_319518_.pipeline().addAfter("decoder", "bundler", packetbundlepacker)
                );
            }

            syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler$inboundconfigurationtask));
        }
    }

    public void setupOutboundProtocol(ProtocolInfo<?> p_319974_) {
        if (p_319974_.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + p_319974_.id());
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler$outboundconfigurationtask = UnconfiguredPipelineHandler.setupOutboundProtocol(
                p_319974_
            );
            BundlerInfo bundlerinfo = p_319974_.bundlerInfo();
            if (bundlerinfo != null) {
                PacketBundleUnpacker packetbundleunpacker = new PacketBundleUnpacker(bundlerinfo);
                unconfiguredpipelinehandler$outboundconfigurationtask = unconfiguredpipelinehandler$outboundconfigurationtask.andThen(
                    p_319516_ -> {
                        p_319516_.pipeline().addAfter("encoder", "unbundler", packetbundleunpacker);
                        // Neo: our handlers must be between the encoder and the unbundler, so re-inject them
                        // Note, this call must be inside the .andThen lambda, or it will actually run before the unbundler gets added.
                        net.neoforged.neoforge.network.filters.NetworkFilters.injectIfNecessary(this);
                    }
                );
            }

            boolean flag = p_319974_.id() == ConnectionProtocol.LOGIN;
            syncAfterConfigurationChange(
                this.channel.writeAndFlush(unconfiguredpipelinehandler$outboundconfigurationtask.andThen(p_319527_ -> this.sendLoginDisconnect = flag))
            );
        }
    }

    public void setListenerForServerboundHandshake(PacketListener p_294829_) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && p_294829_.flow() == PacketFlow.SERVERBOUND && p_294829_.protocol() == INITIAL_PROTOCOL.id()) {
            this.packetListener = p_294829_;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String p_294457_, int p_294964_, ClientStatusPacketListener p_295216_) {
        this.initiateServerboundConnection(p_294457_, p_294964_, StatusProtocols.SERVERBOUND, StatusProtocols.CLIENTBOUND, p_295216_, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String p_294126_, int p_296126_, ClientLoginPacketListener p_296049_) {
        this.initiateServerboundConnection(p_294126_, p_296126_, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND, p_296049_, ClientIntent.LOGIN);
    }

    public <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundPlayConnection(
        String p_320416_, int p_320043_, ProtocolInfo<S> p_320648_, ProtocolInfo<C> p_320919_, C p_319801_, boolean p_319971_
    ) {
        this.initiateServerboundConnection(p_320416_, p_320043_, p_320648_, p_320919_, p_319801_, p_319971_ ? ClientIntent.TRANSFER : ClientIntent.LOGIN);
    }

    private <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundConnection(
        String p_294633_, int p_295471_, ProtocolInfo<S> p_320358_, ProtocolInfo<C> p_320237_, C p_320783_, ClientIntent p_294281_
    ) {
        if (p_320358_.id() != p_320237_.id()) {
            throw new IllegalStateException("Mismatched initial protocols");
        } else {
            this.disconnectListener = p_320783_;
            this.runOnceConnected(
                p_319525_ -> {
                    this.setupInboundProtocol(p_320237_, p_320783_);
                    p_319525_.sendPacket(
                        new ClientIntentionPacket(SharedConstants.getCurrentVersion().getProtocolVersion(), p_294633_, p_295471_, p_294281_), null, true
                    );
                    this.setupOutboundProtocol(p_320358_);
                }
            );
        }
    }

    public void send(Packet<?> p_129513_) {
        this.send(p_129513_, null);
    }

    public void send(Packet<?> p_243248_, @Nullable PacketSendListener p_243316_) {
        this.send(p_243248_, p_243316_, true);
    }

    public void send(Packet<?> p_295839_, @Nullable PacketSendListener p_294866_, boolean p_294265_) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(p_295839_, p_294866_, p_294265_);
        } else {
            this.pendingActions.add(p_293706_ -> p_293706_.sendPacket(p_295839_, p_294866_, p_294265_));
        }
    }

    public void runOnceConnected(Consumer<Connection> p_294674_) {
        if (this.isConnected()) {
            this.flushQueue();
            p_294674_.accept(this);
        } else {
            this.pendingActions.add(p_294674_);
        }
    }

    private void sendPacket(Packet<?> p_129521_, @Nullable PacketSendListener p_243246_, boolean p_294510_) {
        this.sentPackets++;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(p_129521_, p_243246_, p_294510_);
        } else {
            this.channel.eventLoop().execute(() -> this.doSendPacket(p_129521_, p_243246_, p_294510_));
        }
    }

    private void doSendPacket(Packet<?> p_243260_, @Nullable PacketSendListener p_243290_, boolean p_294125_) {
        ChannelFuture channelfuture = p_294125_ ? this.channel.writeAndFlush(p_243260_) : this.channel.write(p_243260_);
        if (p_243290_ != null) {
            channelfuture.addListener(p_243167_ -> {
                if (p_243167_.isSuccess()) {
                    p_243290_.onSuccess();
                } else {
                    Packet<?> packet = p_243290_.onFailure();
                    if (packet != null) {
                        ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet);
                        channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
            });
        }

        channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public void flushChannel() {
        if (this.isConnected()) {
            this.flush();
        } else {
            this.pendingActions.add(Connection::flush);
        }
    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> this.channel.flush());
        }
    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            synchronized (this.pendingActions) {
                Consumer<Connection> consumer;
                while ((consumer = this.pendingActions.poll()) != null) {
                    consumer.accept(this);
                }
            }
        }
    }

    public void tick() {
        this.flushQueue();
        if (this.packetListener instanceof TickablePacketListener tickablepacketlistener) {
            tickablepacketlistener.tick();
        }

        if (!this.isConnected() && !this.disconnectionHandled) {
            this.handleDisconnection();
        }

        if (this.channel != null) {
            this.channel.flush();
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }
    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float)this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float)this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean p_295875_) {
        if (this.address == null) {
            return "local";
        } else {
            return p_295875_ ? net.neoforged.neoforge.network.DualStackUtils.getAddressString(this.address) : "IP hidden";
        }
    }

    public void disconnect(Component p_129508_) {
        this.disconnect(new DisconnectionDetails(p_129508_));
    }

    public void disconnect(DisconnectionDetails p_350867_) {
        if (this.channel == null) {
            this.delayedDisconnect = p_350867_;
        }

        if (this.isConnected()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectionDetails = p_350867_;
        }
    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress p_178301_, boolean p_178302_, @Nullable LocalSampleLogger p_324075_) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        if (p_324075_ != null) {
            connection.setBandwidthLogger(p_324075_);
        }

        ChannelFuture channelfuture = connect(p_178301_, p_178302_, connection);
        channelfuture.syncUninterruptibly();
        return connection;
    }

    public static ChannelFuture connect(InetSocketAddress p_290034_, boolean p_290035_, final Connection p_290031_) {
        net.neoforged.neoforge.network.DualStackUtils.checkIPv6(p_290034_.getAddress());
        Class<? extends SocketChannel> oclass;
        EventLoopGroup eventloopgroup;
        if (Epoll.isAvailable() && p_290035_) {
            oclass = EpollSocketChannel.class;
            eventloopgroup = NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            oclass = NioSocketChannel.class;
            eventloopgroup = NETWORK_WORKER_GROUP.get();
        }

        return new Bootstrap().group(eventloopgroup).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel p_129552_) {
                try {
                    p_129552_.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                }

                ChannelPipeline channelpipeline = p_129552_.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, false, p_290031_.bandwidthDebugMonitor);
                p_290031_.configurePacketHandler(channelpipeline);
            }
        }).channel(oclass).connect(p_290034_.getAddress(), p_290034_.getPort());
    }

    private static String outboundHandlerName(boolean p_319809_) {
        return p_319809_ ? "encoder" : "outbound_config";
    }

    private static String inboundHandlerName(boolean p_320267_) {
        return p_320267_ ? "decoder" : "inbound_config";
    }

    public void configurePacketHandler(ChannelPipeline p_302007_) {
        p_302007_.addLast("hackfix", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext p_320587_, Object p_320392_, ChannelPromise p_320515_) throws Exception {
                super.write(p_320587_, p_320392_, p_320515_);
            }
        }).addLast("packet_handler", this);
    }

    public static void configureSerialization(ChannelPipeline p_265436_, PacketFlow p_265104_, boolean p_341592_, @Nullable BandwidthDebugMonitor p_299246_) {
        PacketFlow packetflow = p_265104_.getOpposite();
        boolean flag = p_265104_ == PacketFlow.SERVERBOUND;
        boolean flag1 = packetflow == PacketFlow.SERVERBOUND;
        p_265436_.addLast("splitter", createFrameDecoder(p_299246_, p_341592_))
            .addLast(new FlowControlHandler())
            .addLast(inboundHandlerName(flag), (ChannelHandler)(flag ? new PacketDecoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Inbound()))
            .addLast("prepender", createFrameEncoder(p_341592_))
            .addLast(outboundHandlerName(flag1), (ChannelHandler)(flag1 ? new PacketEncoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Outbound()));
    }

    private static ChannelOutboundHandler createFrameEncoder(boolean p_341616_) {
        return (ChannelOutboundHandler)(p_341616_ ? new NoOpFrameEncoder() : new Varint21LengthFieldPrepender());
    }

    private static ChannelInboundHandler createFrameDecoder(@Nullable BandwidthDebugMonitor p_341605_, boolean p_341702_) {
        if (!p_341702_) {
            return new Varint21FrameDecoder(p_341605_);
        } else {
            return (ChannelInboundHandler)(p_341605_ != null ? new MonitorFrameDecoder(p_341605_) : new NoOpFrameDecoder());
        }
    }

    public static void configureInMemoryPipeline(ChannelPipeline p_295541_, PacketFlow p_294540_) {
        configureSerialization(p_295541_, p_294540_, true, null);
    }

    public static Connection connectToLocalServer(SocketAddress p_129494_) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        new Bootstrap().group(LOCAL_WORKER_GROUP.get()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel p_320092_) {
                ChannelPipeline channelpipeline = p_320092_.pipeline();
                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                connection.configurePacketHandler(channelpipeline);
            }
        }).channel(LocalChannel.class).connect(p_129494_).syncUninterruptibly();
        return connection;
    }

    public void setEncryptionKey(Cipher p_129496_, Cipher p_129497_) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(p_129496_));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(p_129497_));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    @Nullable
    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public DisconnectionDetails getDisconnectionDetails() {
        return this.disconnectionDetails;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }
    }

    public void setupCompression(int p_129485_, boolean p_182682_) {
        if (p_129485_ >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder compressiondecoder) {
                compressiondecoder.setThreshold(p_129485_, p_182682_);
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(p_129485_, p_182682_));
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder compressionencoder) {
                compressionencoder.setThreshold(p_129485_);
            } else {
                this.channel.pipeline().addAfter("prepender", "compress", new CompressionEncoder(p_129485_));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;
                if (packetlistener1 != null) {
                    DisconnectionDetails disconnectiondetails = Objects.requireNonNullElseGet(
                        this.getDisconnectionDetails(), () -> new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic"))
                    );
                    packetlistener1.onDisconnect(disconnectiondetails);
                }
            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(LocalSampleLogger p_323799_) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(p_323799_);
    }

    public Channel channel() {
        return this.channel;
    }

    public PacketFlow getDirection() {
        return this.receiving;
    }

    public ProtocolInfo<?> getInboundProtocol() {
        return Objects.requireNonNull(this.inboundProtocol, "Inbound protocol not set?");
    }
}
