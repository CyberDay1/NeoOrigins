package net.minecraft.world.level.chunk.status;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

public class ChunkStatusTasks {
    private static boolean isLighted(ChunkAccess p_330524_) {
        return p_330524_.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && p_330524_.isLightCorrect();
    }

    static CompletableFuture<ChunkAccess> passThrough(
        WorldGenContext p_347652_, ChunkStep p_347508_, StaticCache2D<GenerationChunkHolder> p_347686_, ChunkAccess p_347612_
    ) {
        return CompletableFuture.completedFuture(p_347612_);
    }

    static CompletableFuture<ChunkAccess> generateStructureStarts(
        WorldGenContext p_331607_, ChunkStep p_347505_, StaticCache2D<GenerationChunkHolder> p_347639_, ChunkAccess p_330224_
    ) {
        ServerLevel serverlevel = p_331607_.level();
        if (serverlevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
            p_331607_.generator()
                .createStructures(
                    serverlevel.registryAccess(),
                    serverlevel.getChunkSource().getGeneratorState(),
                    serverlevel.structureManager(),
                    p_330224_,
                    p_331607_.structureManager()
                );
        }

        serverlevel.onStructureStartsAvailable(p_330224_);
        return CompletableFuture.completedFuture(p_330224_);
    }

    static CompletableFuture<ChunkAccess> loadStructureStarts(
        WorldGenContext p_331337_, ChunkStep p_347700_, StaticCache2D<GenerationChunkHolder> p_347697_, ChunkAccess p_331647_
    ) {
        p_331337_.level().onStructureStartsAvailable(p_331647_);
        return CompletableFuture.completedFuture(p_331647_);
    }

    static CompletableFuture<ChunkAccess> generateStructureReferences(
        WorldGenContext p_331037_, ChunkStep p_347588_, StaticCache2D<GenerationChunkHolder> p_347555_, ChunkAccess p_331453_
    ) {
        ServerLevel serverlevel = p_331037_.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347555_, p_347588_, p_331453_);
        p_331037_.generator().createReferences(worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), p_331453_);
        return CompletableFuture.completedFuture(p_331453_);
    }

    static CompletableFuture<ChunkAccess> generateBiomes(
        WorldGenContext p_331619_, ChunkStep p_347659_, StaticCache2D<GenerationChunkHolder> p_347729_, ChunkAccess p_332054_
    ) {
        ServerLevel serverlevel = p_331619_.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347729_, p_347659_, p_332054_);
        return p_331619_.generator()
            .createBiomes(
                serverlevel.getChunkSource().randomState(),
                Blender.of(worldgenregion),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                p_332054_
            );
    }

    static CompletableFuture<ChunkAccess> generateNoise(
        WorldGenContext p_331452_, ChunkStep p_347576_, StaticCache2D<GenerationChunkHolder> p_347613_, ChunkAccess p_330927_
    ) {
        ServerLevel serverlevel = p_331452_.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347613_, p_347576_, p_330927_);
        return p_331452_.generator()
            .fillFromNoise(
                Blender.of(worldgenregion),
                serverlevel.getChunkSource().randomState(),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                p_330927_
            )
            .thenApply(p_330442_ -> {
                if (p_330442_ instanceof ProtoChunk protochunk) {
                    BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();
                    if (belowzeroretrogen != null) {
                        BelowZeroRetrogen.replaceOldBedrock(protochunk);
                        if (belowzeroretrogen.hasBedrockHoles()) {
                            belowzeroretrogen.applyBedrockMask(protochunk);
                        }
                    }
                }

                return (ChunkAccess)p_330442_;
            });
    }

    static CompletableFuture<ChunkAccess> generateSurface(
        WorldGenContext p_331468_, ChunkStep p_347720_, StaticCache2D<GenerationChunkHolder> p_347636_, ChunkAccess p_331100_
    ) {
        ServerLevel serverlevel = p_331468_.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347636_, p_347720_, p_331100_);
        p_331468_.generator()
            .buildSurface(
                worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), serverlevel.getChunkSource().randomState(), p_331100_
            );
        return CompletableFuture.completedFuture(p_331100_);
    }

    static CompletableFuture<ChunkAccess> generateCarvers(
        WorldGenContext p_331858_, ChunkStep p_347728_, StaticCache2D<GenerationChunkHolder> p_347581_, ChunkAccess p_330818_
    ) {
        ServerLevel serverlevel = p_331858_.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347581_, p_347728_, p_330818_);
        if (p_330818_ instanceof ProtoChunk protochunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(worldgenregion, protochunk);
        }

        p_331858_.generator()
            .applyCarvers(
                worldgenregion,
                serverlevel.getSeed(),
                serverlevel.getChunkSource().randomState(),
                serverlevel.getBiomeManager(),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                p_330818_,
                GenerationStep.Carving.AIR
            );
        return CompletableFuture.completedFuture(p_330818_);
    }

    static CompletableFuture<ChunkAccess> generateFeatures(
        WorldGenContext p_330280_, ChunkStep p_347560_, StaticCache2D<GenerationChunkHolder> p_347674_, ChunkAccess p_332040_
    ) {
        ServerLevel serverlevel = p_330280_.level();
        Heightmap.primeHeightmaps(
            p_332040_,
            EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE)
        );
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, p_347674_, p_347560_, p_332040_);
        p_330280_.generator().applyBiomeDecoration(worldgenregion, p_332040_, serverlevel.structureManager().forWorldGenRegion(worldgenregion));
        Blender.generateBorderTicks(worldgenregion, p_332040_);
        return CompletableFuture.completedFuture(p_332040_);
    }

    static CompletableFuture<ChunkAccess> initializeLight(
        WorldGenContext p_347725_, ChunkStep p_347727_, StaticCache2D<GenerationChunkHolder> p_347486_, ChunkAccess p_331196_
    ) {
        ThreadedLevelLightEngine threadedlevellightengine = p_347725_.lightEngine();
        p_331196_.initializeLightSources();
        ((ProtoChunk)p_331196_).setLightEngine(threadedlevellightengine);
        boolean flag = isLighted(p_331196_);
        return threadedlevellightengine.initializeLight(p_331196_, flag);
    }

    static CompletableFuture<ChunkAccess> light(
        WorldGenContext p_347668_, ChunkStep p_347535_, StaticCache2D<GenerationChunkHolder> p_347456_, ChunkAccess p_347599_
    ) {
        boolean flag = isLighted(p_347599_);
        return p_347668_.lightEngine().lightChunk(p_347599_, flag);
    }

    static CompletableFuture<ChunkAccess> generateSpawn(
        WorldGenContext p_330441_, ChunkStep p_347702_, StaticCache2D<GenerationChunkHolder> p_347488_, ChunkAccess p_331907_
    ) {
        if (!p_331907_.isUpgrading()) {
            p_330441_.generator().spawnOriginalMobs(new WorldGenRegion(p_330441_.level(), p_347488_, p_347702_, p_331907_));
        }

        return CompletableFuture.completedFuture(p_331907_);
    }

    static CompletableFuture<ChunkAccess> full(
        WorldGenContext p_347565_, ChunkStep p_347455_, StaticCache2D<GenerationChunkHolder> p_347586_, ChunkAccess p_347656_
    ) {
        ChunkPos chunkpos = p_347656_.getPos();
        GenerationChunkHolder generationchunkholder = p_347586_.get(chunkpos.x, chunkpos.z);
        return CompletableFuture.supplyAsync(
            () -> {
                ProtoChunk protochunk = (ProtoChunk)p_347656_;
                ServerLevel serverlevel = p_347565_.level();
                LevelChunk levelchunk;
                if (protochunk instanceof ImposterProtoChunk) {
                    levelchunk = ((ImposterProtoChunk)protochunk).getWrapped();
                } else {
                    levelchunk = new LevelChunk(serverlevel, protochunk, p_347400_ -> postLoadProtoChunk(serverlevel, protochunk.getEntities()));
                    generationchunkholder.replaceProtoChunk(new ImposterProtoChunk(levelchunk, false));
                }

                levelchunk.setFullStatus(generationchunkholder::getFullStatus);
                try {
                generationchunkholder.currentlyLoading = levelchunk; // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
                levelchunk.runPostLoad();
                } finally {
                    generationchunkholder.currentlyLoading = null; // Neo: Stop bypassing the future chain.
                }
                levelchunk.setLoaded(true);
                try {
                generationchunkholder.currentlyLoading = levelchunk; // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
                levelchunk.registerAllBlockEntitiesAfterLevelLoad();
                levelchunk.registerTickContainerInLevel(serverlevel);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(levelchunk, !(protochunk instanceof ImposterProtoChunk)));
                } finally {
                    generationchunkholder.currentlyLoading = null; // Neo: Stop bypassing the future chain.
                }
                return levelchunk;
            },
            p_347404_ -> p_347565_.mainThreadMailBox()
                    .tell(ChunkTaskPriorityQueueSorter.message(p_347404_, chunkpos.toLong(), generationchunkholder::getTicketLevel))
        );
    }

    private static void postLoadProtoChunk(ServerLevel p_347492_, List<CompoundTag> p_347609_) {
        if (!p_347609_.isEmpty()) {
            p_347492_.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(p_347609_, p_347492_));
        }
    }
}
