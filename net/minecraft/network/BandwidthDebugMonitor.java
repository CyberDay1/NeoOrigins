package net.minecraft.network;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.debugchart.LocalSampleLogger;

public class BandwidthDebugMonitor {
    private final AtomicInteger bytesReceived = new AtomicInteger();
    private final LocalSampleLogger bandwidthLogger;

    public BandwidthDebugMonitor(LocalSampleLogger p_323860_) {
        this.bandwidthLogger = p_323860_;
    }

    public void onReceive(int p_298836_) {
        this.bytesReceived.getAndAdd(p_298836_);
    }

    public void tick() {
        this.bandwidthLogger.logSample((long)this.bytesReceived.getAndSet(0));
    }
}
