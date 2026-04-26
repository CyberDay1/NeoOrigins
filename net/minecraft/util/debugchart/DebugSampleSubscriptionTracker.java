package net.minecraft.util.debugchart;

import com.google.common.collect.Maps;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public class DebugSampleSubscriptionTracker {
    public static final int STOP_SENDING_AFTER_TICKS = 200;
    public static final int STOP_SENDING_AFTER_MS = 10000;
    private final PlayerList playerList;
    private final EnumMap<RemoteDebugSampleType, Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt>> subscriptions;
    private final Queue<DebugSampleSubscriptionTracker.SubscriptionRequest> subscriptionRequestQueue = new LinkedList<>();

    public DebugSampleSubscriptionTracker(PlayerList p_323956_) {
        this.playerList = p_323956_;
        this.subscriptions = new EnumMap<>(RemoteDebugSampleType.class);

        for (RemoteDebugSampleType remotedebugsampletype : RemoteDebugSampleType.values()) {
            this.subscriptions.put(remotedebugsampletype, Maps.newHashMap());
        }
    }

    public boolean shouldLogSamples(RemoteDebugSampleType p_324431_) {
        return !this.subscriptions.get(p_324431_).isEmpty();
    }

    public void broadcast(ClientboundDebugSamplePacket p_324398_) {
        for (ServerPlayer serverplayer : this.subscriptions.get(p_324398_.debugSampleType()).keySet()) {
            serverplayer.connection.send(p_324398_);
        }
    }

    public void subscribe(ServerPlayer p_324211_, RemoteDebugSampleType p_324355_) {
        if (this.playerList.isOp(p_324211_.getGameProfile())) {
            this.subscriptionRequestQueue.add(new DebugSampleSubscriptionTracker.SubscriptionRequest(p_324211_, p_324355_));
        }
    }

    public void tick(int p_323889_) {
        long i = Util.getMillis();
        this.handleSubscriptions(i, p_323889_);
        this.handleUnsubscriptions(i, p_323889_);
    }

    private void handleSubscriptions(long p_324350_, int p_323574_) {
        for (DebugSampleSubscriptionTracker.SubscriptionRequest debugsamplesubscriptiontracker$subscriptionrequest : this.subscriptionRequestQueue) {
            this.subscriptions
                .get(debugsamplesubscriptiontracker$subscriptionrequest.sampleType())
                .put(
                    debugsamplesubscriptiontracker$subscriptionrequest.player(), new DebugSampleSubscriptionTracker.SubscriptionStartedAt(p_324350_, p_323574_)
                );
        }
    }

    private void handleUnsubscriptions(long p_323971_, int p_324253_) {
        for (Map<ServerPlayer, DebugSampleSubscriptionTracker.SubscriptionStartedAt> map : this.subscriptions.values()) {
            map.entrySet()
                .removeIf(
                    p_323887_ -> {
                        boolean flag = !this.playerList.isOp(p_323887_.getKey().getGameProfile());
                        DebugSampleSubscriptionTracker.SubscriptionStartedAt debugsamplesubscriptiontracker$subscriptionstartedat = p_323887_.getValue();
                        return flag
                            || p_324253_ > debugsamplesubscriptiontracker$subscriptionstartedat.tick() + 200
                                && p_323971_ > debugsamplesubscriptiontracker$subscriptionstartedat.millis() + 10000L;
                    }
                );
        }
    }

    static record SubscriptionRequest(ServerPlayer player, RemoteDebugSampleType sampleType) {
    }

    static record SubscriptionStartedAt(long millis, int tick) {
    }
}
