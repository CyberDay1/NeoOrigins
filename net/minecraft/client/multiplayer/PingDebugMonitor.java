package net.minecraft.client.multiplayer;

import net.minecraft.Util;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PingDebugMonitor {
    private final ClientPacketListener connection;
    private final LocalSampleLogger delayTimer;

    public PingDebugMonitor(ClientPacketListener p_298598_, LocalSampleLogger p_323903_) {
        this.connection = p_298598_;
        this.delayTimer = p_323903_;
    }

    public void tick() {
        this.connection.send(new ServerboundPingRequestPacket(Util.getMillis()));
    }

    public void onPongReceived(ClientboundPongResponsePacket p_320694_) {
        this.delayTimer.logSample(Util.getMillis() - p_320694_.time());
    }
}
