package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    private final Long2ObjectMap<RenderRegionCache.ChunkInfo> chunkInfoCache = new Long2ObjectOpenHashMap<>();

    @Nullable
    public RenderChunkRegion createRegion(Level p_200466_, SectionPos p_350879_) {
        return createRegion(p_200466_, p_350879_, true);
    }

    @Nullable
    public RenderChunkRegion createRegion(Level p_200466_, SectionPos p_350879_, boolean nullForEmpty) {
        RenderRegionCache.ChunkInfo renderregioncache$chunkinfo = this.getChunkInfo(p_200466_, p_350879_.x(), p_350879_.z());
        if (nullForEmpty && renderregioncache$chunkinfo.chunk().isSectionEmpty(p_350879_.y())) {
            return null;
        } else {
            int i = p_350879_.x() - 1;
            int j = p_350879_.z() - 1;
            int k = p_350879_.x() + 1;
            int l = p_350879_.z() + 1;
            RenderChunk[] arenderchunk = new RenderChunk[9];

            for (int i1 = j; i1 <= l; i1++) {
                for (int j1 = i; j1 <= k; j1++) {
                    int k1 = RenderChunkRegion.index(i, j, j1, i1);
                    RenderRegionCache.ChunkInfo renderregioncache$chunkinfo1 = j1 == p_350879_.x() && i1 == p_350879_.z()
                        ? renderregioncache$chunkinfo
                        : this.getChunkInfo(p_200466_, j1, i1);
                    arenderchunk[k1] = renderregioncache$chunkinfo1.renderChunk();
                }
            }

            int sectionMinY = p_350879_.getY() - RenderChunkRegion.RADIUS;
            int sectionMaxY = p_350879_.getY() + RenderChunkRegion.RADIUS;
            var modelDataManager = p_200466_.getModelDataManager().snapshotSectionRegion(i, sectionMinY, j, k, sectionMaxY, l);
            return new RenderChunkRegion(p_200466_, i, j, arenderchunk, modelDataManager);
        }
    }

    private RenderRegionCache.ChunkInfo getChunkInfo(Level p_350834_, int p_350803_, int p_350907_) {
        return this.chunkInfoCache
            .computeIfAbsent(
                ChunkPos.asLong(p_350803_, p_350907_),
                p_200464_ -> new RenderRegionCache.ChunkInfo(p_350834_.getChunk(ChunkPos.getX(p_200464_), ChunkPos.getZ(p_200464_)))
            );
    }

    @OnlyIn(Dist.CLIENT)
    static final class ChunkInfo {
        private final LevelChunk chunk;
        @Nullable
        private RenderChunk renderChunk;

        ChunkInfo(LevelChunk p_200479_) {
            this.chunk = p_200479_;
        }

        public LevelChunk chunk() {
            return this.chunk;
        }

        public RenderChunk renderChunk() {
            if (this.renderChunk == null) {
                this.renderChunk = new RenderChunk(this.chunk);
            }

            return this.renderChunk;
        }
    }
}
