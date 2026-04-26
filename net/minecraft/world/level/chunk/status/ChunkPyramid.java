package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public record ChunkPyramid(ImmutableList<ChunkStep> steps) {
    public static final ChunkPyramid GENERATION_PYRAMID = new ChunkPyramid.Builder()
        .step(ChunkStatus.EMPTY, p_347683_ -> p_347683_)
        .step(ChunkStatus.STRUCTURE_STARTS, p_347517_ -> p_347517_.setTask(ChunkStatusTasks::generateStructureStarts))
        .step(
            ChunkStatus.STRUCTURE_REFERENCES,
            p_347504_ -> p_347504_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).setTask(ChunkStatusTasks::generateStructureReferences)
        )
        .step(ChunkStatus.BIOMES, p_347660_ -> p_347660_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).setTask(ChunkStatusTasks::generateBiomes))
        .step(
            ChunkStatus.NOISE,
            p_347641_ -> p_347641_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8)
                    .addRequirement(ChunkStatus.BIOMES, 1)
                    .blockStateWriteRadius(0)
                    .setTask(ChunkStatusTasks::generateNoise)
        )
        .step(
            ChunkStatus.SURFACE,
            p_347669_ -> p_347669_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8)
                    .addRequirement(ChunkStatus.BIOMES, 1)
                    .blockStateWriteRadius(0)
                    .setTask(ChunkStatusTasks::generateSurface)
        )
        .step(
            ChunkStatus.CARVERS,
            p_347578_ -> p_347578_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8).blockStateWriteRadius(0).setTask(ChunkStatusTasks::generateCarvers)
        )
        .step(
            ChunkStatus.FEATURES,
            p_347654_ -> p_347654_.addRequirement(ChunkStatus.STRUCTURE_STARTS, 8)
                    .addRequirement(ChunkStatus.CARVERS, 1)
                    .blockStateWriteRadius(1)
                    .setTask(ChunkStatusTasks::generateFeatures)
        )
        .step(ChunkStatus.INITIALIZE_LIGHT, p_347525_ -> p_347525_.setTask(ChunkStatusTasks::initializeLight))
        .step(ChunkStatus.LIGHT, p_347515_ -> p_347515_.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1).setTask(ChunkStatusTasks::light))
        .step(ChunkStatus.SPAWN, p_347644_ -> p_347644_.addRequirement(ChunkStatus.BIOMES, 1).setTask(ChunkStatusTasks::generateSpawn))
        .step(ChunkStatus.FULL, p_347696_ -> p_347696_.setTask(ChunkStatusTasks::full))
        .build();
    public static final ChunkPyramid LOADING_PYRAMID = new ChunkPyramid.Builder()
        .step(ChunkStatus.EMPTY, p_347564_ -> p_347564_)
        .step(ChunkStatus.STRUCTURE_STARTS, p_347676_ -> p_347676_.setTask(ChunkStatusTasks::loadStructureStarts))
        .step(ChunkStatus.STRUCTURE_REFERENCES, p_347653_ -> p_347653_)
        .step(ChunkStatus.BIOMES, p_347461_ -> p_347461_)
        .step(ChunkStatus.NOISE, p_347573_ -> p_347573_)
        .step(ChunkStatus.SURFACE, p_347532_ -> p_347532_)
        .step(ChunkStatus.CARVERS, p_347448_ -> p_347448_)
        .step(ChunkStatus.FEATURES, p_347446_ -> p_347446_)
        .step(ChunkStatus.INITIALIZE_LIGHT, p_347603_ -> p_347603_.setTask(ChunkStatusTasks::initializeLight))
        .step(ChunkStatus.LIGHT, p_347463_ -> p_347463_.addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1).setTask(ChunkStatusTasks::light))
        .step(ChunkStatus.SPAWN, p_347516_ -> p_347516_)
        .step(ChunkStatus.FULL, p_347695_ -> p_347695_.setTask(ChunkStatusTasks::full))
        .build();

    public ChunkStep getStepTo(ChunkStatus p_347527_) {
        return this.steps.get(p_347527_.getIndex());
    }

    public static class Builder {
        private final List<ChunkStep> steps = new ArrayList<>();

        public ChunkPyramid build() {
            return new ChunkPyramid(ImmutableList.copyOf(this.steps));
        }

        public ChunkPyramid.Builder step(ChunkStatus p_347677_, UnaryOperator<ChunkStep.Builder> p_347470_) {
            ChunkStep.Builder chunkstep$builder;
            if (this.steps.isEmpty()) {
                chunkstep$builder = new ChunkStep.Builder(p_347677_);
            } else {
                chunkstep$builder = new ChunkStep.Builder(p_347677_, this.steps.getLast());
            }

            this.steps.add(p_347470_.apply(chunkstep$builder).build());
            return this;
        }
    }
}
