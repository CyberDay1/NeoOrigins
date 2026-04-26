package net.minecraft.client.multiplayer.prediction;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockStatePredictionHandler implements AutoCloseable {
    private final Long2ObjectOpenHashMap<BlockStatePredictionHandler.ServerVerifiedState> serverVerifiedStates = new Long2ObjectOpenHashMap<>();
    private int currentSequenceNr;
    private boolean isPredicting;

    public void retainKnownServerState(BlockPos p_233868_, BlockState p_233869_, LocalPlayer p_233870_) {
        this.serverVerifiedStates
            .compute(
                p_233868_.asLong(),
                (p_352672_, p_352673_) -> p_352673_ != null
                        ? p_352673_.setSequence(this.currentSequenceNr)
                        : new BlockStatePredictionHandler.ServerVerifiedState(this.currentSequenceNr, p_233869_, p_233870_.position())
            );
    }

    public boolean updateKnownServerState(BlockPos p_233865_, BlockState p_233866_) {
        BlockStatePredictionHandler.ServerVerifiedState blockstatepredictionhandler$serververifiedstate = this.serverVerifiedStates.get(p_233865_.asLong());
        if (blockstatepredictionhandler$serververifiedstate == null) {
            return false;
        } else {
            blockstatepredictionhandler$serververifiedstate.setBlockState(p_233866_);
            return true;
        }
    }

    public void endPredictionsUpTo(int p_233857_, ClientLevel p_233858_) {
        ObjectIterator<Entry<BlockStatePredictionHandler.ServerVerifiedState>> objectiterator = this.serverVerifiedStates.long2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<BlockStatePredictionHandler.ServerVerifiedState> entry = objectiterator.next();
            BlockStatePredictionHandler.ServerVerifiedState blockstatepredictionhandler$serververifiedstate = entry.getValue();
            if (blockstatepredictionhandler$serververifiedstate.sequence <= p_233857_) {
                BlockPos blockpos = BlockPos.of(entry.getLongKey());
                objectiterator.remove();
                p_233858_.syncBlockState(
                    blockpos, blockstatepredictionhandler$serververifiedstate.blockState, blockstatepredictionhandler$serververifiedstate.playerPos
                );
                // Neo: Restore the BlockEntity if one was present before the break was cancelled.
                // Fixes MC-36093 and permits correct server-side only cancellation of block changes.
                var verifiedState = blockstatepredictionhandler$serververifiedstate;
                if (verifiedState.snapshot != null && verifiedState.blockState == verifiedState.snapshot.getState()) {
                    if (verifiedState.snapshot.restoreBlockEntity(p_233858_, blockpos)) {
                        // Attempt a re-render if BE data was loaded, since some blocks may depend on it.
                        p_233858_.sendBlockUpdated(blockpos, verifiedState.blockState, verifiedState.blockState, 3);
                    }
                }
            }
        }
    }

    public BlockStatePredictionHandler startPredicting() {
        this.currentSequenceNr++;
        this.isPredicting = true;
        return this;
    }

    @Override
    public void close() {
        this.isPredicting = false;
    }

    public int currentSequence() {
        return this.currentSequenceNr;
    }

    public boolean isPredicting() {
        return this.isPredicting;
    }

    /**
     * Sets the stored BlockSnapshot on the ServerVerifiedState for the given position.
     * This method is only called after {@link #retainKnownServerState}, so we are certain a map entry exists.
     */
    public void retainSnapshot(BlockPos pos, net.neoforged.neoforge.common.util.BlockSnapshot snapshot) {
        this.serverVerifiedStates.get(pos.asLong()).snapshot = snapshot;
    }

    @OnlyIn(Dist.CLIENT)
    static class ServerVerifiedState {
        /**
         * Neo: Used to hold all data necessary for clientside restoration during break denial.
         */
        net.neoforged.neoforge.common.util.BlockSnapshot snapshot;
        final Vec3 playerPos;
        int sequence;
        BlockState blockState;

        ServerVerifiedState(int p_233878_, BlockState p_233879_, Vec3 p_233880_) {
            this.sequence = p_233878_;
            this.blockState = p_233879_;
            this.playerPos = p_233880_;
        }

        BlockStatePredictionHandler.ServerVerifiedState setSequence(int p_233882_) {
            this.sequence = p_233882_;
            return this;
        }

        void setBlockState(BlockState p_233884_) {
            this.blockState = p_233884_;
        }
    }
}
