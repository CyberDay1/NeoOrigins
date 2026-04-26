package net.minecraft.server.network;

import com.google.common.collect.Comparators;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public class PlayerChunkSender {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    private static final float START_CHUNKS_PER_TICK = 9.0F;
    private static final int MAX_UNACKNOWLEDGED_BATCHES = 10;
    private final LongSet pendingChunks = new LongOpenHashSet();
    private final boolean memoryConnection;
    private float desiredChunksPerTick = 9.0F;
    private float batchQuota;
    private int unacknowledgedBatches;
    private int maxUnacknowledgedBatches = 1;

    public PlayerChunkSender(boolean p_294754_) {
        this.memoryConnection = p_294754_;
    }

    public void markChunkPendingToSend(LevelChunk p_296454_) {
        this.pendingChunks.add(p_296454_.getPos().toLong());
    }

    public void dropChunk(ServerPlayer p_294214_, ChunkPos p_294933_) {
        if (!this.pendingChunks.remove(p_294933_.toLong()) && p_294214_.isAlive()) {
            p_294214_.connection.send(new ClientboundForgetLevelChunkPacket(p_294933_));
        }
    }

    public void sendNextChunks(ServerPlayer p_296009_) {
        if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
            float f = Math.max(1.0F, this.desiredChunksPerTick);
            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, f);
            if (!(this.batchQuota < 1.0F)) {
                if (!this.pendingChunks.isEmpty()) {
                    ServerLevel serverlevel = p_296009_.serverLevel();
                    ChunkMap chunkmap = serverlevel.getChunkSource().chunkMap;
                    List<LevelChunk> list = this.collectChunksToSend(chunkmap, p_296009_.chunkPosition());
                    if (!list.isEmpty()) {
                        ServerGamePacketListenerImpl servergamepacketlistenerimpl = p_296009_.connection;
                        this.unacknowledgedBatches++;
                        servergamepacketlistenerimpl.send(ClientboundChunkBatchStartPacket.INSTANCE);

                        for (LevelChunk levelchunk : list) {
                            sendChunk(servergamepacketlistenerimpl, serverlevel, levelchunk);
                        }

                        servergamepacketlistenerimpl.send(new ClientboundChunkBatchFinishedPacket(list.size()));
                        this.batchQuota = this.batchQuota - (float)list.size();
                    }
                }
            }
        }
    }

    private static void sendChunk(ServerGamePacketListenerImpl p_295237_, ServerLevel p_294963_, LevelChunk p_295144_) {
        p_295237_.send(p_295144_.getAuxLightManager(p_295144_.getPos()).sendLightDataTo(
                new ClientboundLevelChunkWithLightPacket(p_295144_, p_294963_.getLightEngine(), null, null)
        ));
        ChunkPos chunkpos = p_295144_.getPos();
        DebugPackets.sendPoiPacketsForChunk(p_294963_, chunkpos);
        net.neoforged.neoforge.event.EventHooks.fireChunkSent(p_295237_.player, p_295144_, p_294963_);
    }

    private List<LevelChunk> collectChunksToSend(ChunkMap p_296053_, ChunkPos p_295659_) {
        int i = Mth.floor(this.batchQuota);
        List<LevelChunk> list;
        if (!this.memoryConnection && this.pendingChunks.size() > i) {
            list = this.pendingChunks
                .stream()
                .collect(Comparators.least(i, Comparator.comparingInt(p_295659_::distanceSquared)))
                .stream()
                .mapToLong(Long::longValue)
                .mapToObj(p_296053_::getChunkToSend)
                .filter(Objects::nonNull)
                .toList();
        } else {
            list = this.pendingChunks
                .longStream()
                .mapToObj(p_296053_::getChunkToSend)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p_294268_ -> p_295659_.distanceSquared(p_294268_.getPos())))
                .toList();
        }

        for (LevelChunk levelchunk : list) {
            this.pendingChunks.remove(levelchunk.getPos().toLong());
        }

        return list;
    }

    public void onChunkBatchReceivedByClient(float p_294462_) {
        this.unacknowledgedBatches--;
        this.desiredChunksPerTick = Double.isNaN((double)p_294462_) ? 0.01F : Mth.clamp(p_294462_, 0.01F, 64.0F);
        if (this.unacknowledgedBatches == 0) {
            this.batchQuota = 1.0F;
        }

        this.maxUnacknowledgedBatches = 10;
    }

    public boolean isPending(long p_296128_) {
        return this.pendingChunks.contains(p_296128_);
    }
}
