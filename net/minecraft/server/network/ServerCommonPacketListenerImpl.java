package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    protected final Connection connection;
    private final boolean transferred;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    /**
     * Holds the current connection type, based on the types of payloads that have been received so far.
     */
    protected net.neoforged.neoforge.network.connection.ConnectionType connectionType;

    public ServerCommonPacketListenerImpl(MinecraftServer p_295057_, Connection p_294822_, CommonListenerCookie p_301980_) {
        this.server = p_295057_;
        this.connection = p_294822_;
        this.keepAliveTime = Util.getMillis();
        this.latency = p_301980_.latency();
        this.transferred = p_301980_.transferred();
        // Neo: Set the connection type based on the cookie from the previous phase.
        this.connectionType = p_301980_.connectionType();
    }

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails p_350605_) {
        if (this.isSingleplayerOwner()) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket p_294627_) {
        if (this.keepAlivePending && p_294627_.getId() == this.keepAliveChallenge) {
            int i = (int)(Util.getMillis() - this.keepAliveTime);
            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
        }
    }

    @Override
    public void handlePong(ServerboundPongPacket p_295142_) {
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket p_294276_) {
        // Neo: Unconditionally handle register/unregister payloads.
        if (p_294276_.payload() instanceof net.neoforged.neoforge.network.payload.MinecraftRegisterPayload minecraftRegisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onMinecraftRegister(this.getConnection(), minecraftRegisterPayload.newChannels());
            return;
        }

        if (p_294276_.payload() instanceof net.neoforged.neoforge.network.payload.MinecraftUnregisterPayload minecraftUnregisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onMinecraftUnregister(this.getConnection(), minecraftUnregisterPayload.forgottenChannels());
            return;
        }

        if (p_294276_.payload() instanceof net.neoforged.neoforge.network.payload.CommonVersionPayload commonVersionPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.checkCommonVersion(this, commonVersionPayload);
            return;
        }

        if (p_294276_.payload() instanceof net.neoforged.neoforge.network.payload.CommonRegisterPayload commonRegisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onCommonRegister(this, commonRegisterPayload);
            return;
        }

        // Neo: Handle modded payloads. Vanilla payloads do not get sent to the modded handling pass. Additional payloads cannot be registered in the minecraft domain.
        if (net.neoforged.neoforge.network.registration.NetworkRegistry.isModdedPayload(p_294276_.payload())) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.handleModdedPayload(this, p_294276_);
            return;
        }
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket p_295695_) {
        PacketUtils.ensureRunningOnSameThread(p_295695_, this, this.server);
        if (p_295695_.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), p_295695_.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket p_320918_) {
        this.disconnect(DISCONNECT_UNEXPECTED_QUERY);
    }

    protected void keepConnectionAlive() {
        this.server.getProfiler().push("keepAlive");
        long i = Util.getMillis();
        if (!this.isSingleplayerOwner() && i - this.keepAliveTime >= 15000L) {
            if (this.keepAlivePending) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (this.checkIfClosed(i)) {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        this.server.getProfiler().pop();
    }

    private boolean checkIfClosed(long p_339648_) {
        if (this.closed) {
            if (p_339648_ - this.closedListenerTime >= 15000L) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> p_294278_) {
        this.send(p_294278_, null);
    }

    @Override
    public void send(Packet<?> p_295099_, @Nullable PacketSendListener p_296321_) {
        net.neoforged.neoforge.network.registration.NetworkRegistry.checkPacket(p_295099_, this);

        if (p_295099_.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(p_295099_, p_296321_, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Packet being sent");
            crashreportcategory.setDetail("Packet class", () -> p_295099_.getClass().getCanonicalName());
            throw new ReportedException(crashreport);
        }
    }

    public void disconnect(Component p_294116_) {
        this.disconnect(new DisconnectionDetails(p_294116_));
    }

    public void disconnect(DisconnectionDetails p_350316_) {
        this.connection.send(new ClientboundDisconnectPacket(p_350316_.reason()), PacketSendListener.thenRun(() -> this.connection.disconnect(p_350316_)));
        this.connection.setReadOnly();
        this.server.executeBlocking(this.connection::handleDisconnection);
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    /**
     * Creates a new cookie for this connection.
     *
     * @param p_301973_ The client information.
     * @return The cookie.
     * @deprecated Use {@link #createCookie(ClientInformation, net.neoforged.neoforge.network.connection.ConnectionType)} instead, keeping the connection type information available.
     */
    @Deprecated
    protected CommonListenerCookie createCookie(ClientInformation p_301973_) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, p_301973_, this.transferred);
    }

    /**
     * Creates a new cookie for this connection.
     *
     * @param p_301973_ The client information.
     * @param connectionType Whether the connection is modded.
     * @return The cookie.
     */
    protected CommonListenerCookie createCookie(ClientInformation p_301973_, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, p_301973_, this.transferred, connectionType);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public net.minecraft.util.thread.ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
        return server;
    }

    @Override
    public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
        return connectionType;
    }
}
