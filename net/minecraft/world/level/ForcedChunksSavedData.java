package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class ForcedChunksSavedData extends SavedData {
    public static final String FILE_ID = "chunks";
    private static final String TAG_FORCED = "Forced";
    private final LongSet chunks;

    public static SavedData.Factory<ForcedChunksSavedData> factory() {
        return new SavedData.Factory<>(ForcedChunksSavedData::new, ForcedChunksSavedData::load, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
    }

    private ForcedChunksSavedData(LongSet p_151482_) {
        this.chunks = p_151482_;
    }

    public ForcedChunksSavedData() {
        this(new LongOpenHashSet());
    }

    public static ForcedChunksSavedData load(CompoundTag p_151484_, HolderLookup.Provider p_323940_) {
        ForcedChunksSavedData savedData = new ForcedChunksSavedData(new LongOpenHashSet(p_151484_.getLongArray("Forced")));
        net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.readModForcedChunks(p_151484_, savedData.blockForcedChunks, savedData.entityForcedChunks);
        return savedData;
    }

    @Override
    public CompoundTag save(CompoundTag p_46120_, HolderLookup.Provider p_324573_) {
        p_46120_.putLongArray("Forced", this.chunks.toLongArray());
        net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.writeModForcedChunks(p_46120_, this.blockForcedChunks, this.entityForcedChunks);
        return p_46120_;
    }

    public LongSet getChunks() {
        return this.chunks;
    }

    // Neo: Keep track of forced loaded chunks caused by entities or blocks.
    private final net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<net.minecraft.core.BlockPos> blockForcedChunks = new net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<>();
    private final net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<java.util.UUID> entityForcedChunks = new net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<>();

    public net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<net.minecraft.core.BlockPos> getBlockForcedChunks() {
        return this.blockForcedChunks;
    }

    public net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<java.util.UUID> getEntityForcedChunks() {
        return this.entityForcedChunks;
    }
}
