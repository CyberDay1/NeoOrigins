package net.minecraft.util.debugchart;

import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;

public class RemoteSampleLogger extends AbstractSampleLogger {
    private final DebugSampleSubscriptionTracker subscriptionTracker;
    private final RemoteDebugSampleType sampleType;

    public RemoteSampleLogger(int p_324142_, DebugSampleSubscriptionTracker p_323690_, RemoteDebugSampleType p_324327_) {
        this(p_324142_, p_323690_, p_324327_, new long[p_324142_]);
    }

    public RemoteSampleLogger(int p_324307_, DebugSampleSubscriptionTracker p_323949_, RemoteDebugSampleType p_323476_, long[] p_323539_) {
        super(p_324307_, p_323539_);
        this.subscriptionTracker = p_323949_;
        this.sampleType = p_323476_;
    }

    @Override
    protected void useSample() {
        this.subscriptionTracker.broadcast(new ClientboundDebugSamplePacket((long[])this.sample.clone(), this.sampleType));
    }
}
