package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class SectionOcclusionGraph {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MINIMUM_ADVANCED_CULLING_DISTANCE = 60;
    private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
    private boolean needsFullUpdate = true;
    @Nullable
    private Future<?> fullUpdateTask;
    @Nullable
    private ViewArea viewArea;
    private final AtomicReference<SectionOcclusionGraph.GraphState> currentGraph = new AtomicReference<>();
    private final AtomicReference<SectionOcclusionGraph.GraphEvents> nextGraphEvents = new AtomicReference<>();
    private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);

    public void waitAndReset(@Nullable ViewArea p_294431_) {
        if (this.fullUpdateTask != null) {
            try {
                this.fullUpdateTask.get();
                this.fullUpdateTask = null;
            } catch (Exception exception) {
                LOGGER.warn("Full update failed", (Throwable)exception);
            }
        }

        this.viewArea = p_294431_;
        if (p_294431_ != null) {
            this.currentGraph.set(new SectionOcclusionGraph.GraphState(p_294431_.sections.length));
            this.invalidate();
        } else {
            this.currentGraph.set(null);
        }
    }

    public void invalidate() {
        this.needsFullUpdate = true;
    }

    public void addSectionsInFrustum(Frustum p_294180_, List<SectionRenderDispatcher.RenderSection> p_296160_) {
        for (SectionOcclusionGraph.Node sectionocclusiongraph$node : this.currentGraph.get().storage().renderSections) {
            if (p_294180_.isVisible(sectionocclusiongraph$node.section.getBoundingBox())) {
                p_296160_.add(sectionocclusiongraph$node.section);
            }
        }
    }

    public boolean consumeFrustumUpdate() {
        return this.needsFrustumUpdate.compareAndSet(true, false);
    }

    public void onChunkLoaded(ChunkPos p_294122_) {
        SectionOcclusionGraph.GraphEvents sectionocclusiongraph$graphevents = this.nextGraphEvents.get();
        if (sectionocclusiongraph$graphevents != null) {
            this.addNeighbors(sectionocclusiongraph$graphevents, p_294122_);
        }

        SectionOcclusionGraph.GraphEvents sectionocclusiongraph$graphevents1 = this.currentGraph.get().events;
        if (sectionocclusiongraph$graphevents1 != sectionocclusiongraph$graphevents) {
            this.addNeighbors(sectionocclusiongraph$graphevents1, p_294122_);
        }
    }

    public void onSectionCompiled(SectionRenderDispatcher.RenderSection p_295414_) {
        SectionOcclusionGraph.GraphEvents sectionocclusiongraph$graphevents = this.nextGraphEvents.get();
        if (sectionocclusiongraph$graphevents != null) {
            sectionocclusiongraph$graphevents.sectionsToPropagateFrom.add(p_295414_);
        }

        SectionOcclusionGraph.GraphEvents sectionocclusiongraph$graphevents1 = this.currentGraph.get().events;
        if (sectionocclusiongraph$graphevents1 != sectionocclusiongraph$graphevents) {
            sectionocclusiongraph$graphevents1.sectionsToPropagateFrom.add(p_295414_);
        }
    }

    public void update(boolean p_294298_, Camera p_294529_, Frustum p_294426_, List<SectionRenderDispatcher.RenderSection> p_295280_) {
        Vec3 vec3 = p_294529_.getPosition();
        if (this.needsFullUpdate && (this.fullUpdateTask == null || this.fullUpdateTask.isDone())) {
            this.scheduleFullUpdate(p_294298_, p_294529_, vec3);
        }

        this.runPartialUpdate(p_294298_, p_294426_, p_295280_, vec3);
    }

    private void scheduleFullUpdate(boolean p_294514_, Camera p_295663_, Vec3 p_295096_) {
        this.needsFullUpdate = false;
        this.fullUpdateTask = Util.backgroundExecutor().submit(() -> {
            SectionOcclusionGraph.GraphState sectionocclusiongraph$graphstate = new SectionOcclusionGraph.GraphState(this.viewArea.sections.length);
            this.nextGraphEvents.set(sectionocclusiongraph$graphstate.events);
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
            this.initializeQueueForFullUpdate(p_295663_, queue);
            queue.forEach(p_295724_ -> sectionocclusiongraph$graphstate.storage.sectionToNodeMap.put(p_295724_.section, p_295724_));
            this.runUpdates(sectionocclusiongraph$graphstate.storage, p_295096_, queue, p_294514_, p_294678_ -> {
            });
            this.currentGraph.set(sectionocclusiongraph$graphstate);
            this.nextGraphEvents.set(null);
            this.needsFrustumUpdate.set(true);
        });
    }

    private void runPartialUpdate(boolean p_294795_, Frustum p_294341_, List<SectionRenderDispatcher.RenderSection> p_294796_, Vec3 p_295915_) {
        SectionOcclusionGraph.GraphState sectionocclusiongraph$graphstate = this.currentGraph.get();
        this.queueSectionsWithNewNeighbors(sectionocclusiongraph$graphstate);
        if (!sectionocclusiongraph$graphstate.events.sectionsToPropagateFrom.isEmpty()) {
            Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();

            while (!sectionocclusiongraph$graphstate.events.sectionsToPropagateFrom.isEmpty()) {
                SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = sectionocclusiongraph$graphstate.events
                    .sectionsToPropagateFrom
                    .poll();
                SectionOcclusionGraph.Node sectionocclusiongraph$node = sectionocclusiongraph$graphstate.storage
                    .sectionToNodeMap
                    .get(sectionrenderdispatcher$rendersection);
                if (sectionocclusiongraph$node != null && sectionocclusiongraph$node.section == sectionrenderdispatcher$rendersection) {
                    queue.add(sectionocclusiongraph$node);
                }
            }

            Frustum frustum = LevelRenderer.offsetFrustum(p_294341_);
            Consumer<SectionRenderDispatcher.RenderSection> consumer = p_295778_ -> {
                if (frustum.isVisible(p_295778_.getBoundingBox())) {
                    p_294796_.add(p_295778_);
                }
            };
            this.runUpdates(sectionocclusiongraph$graphstate.storage, p_295915_, queue, p_294795_, consumer);
        }
    }

    private void queueSectionsWithNewNeighbors(SectionOcclusionGraph.GraphState p_296471_) {
        LongIterator longiterator = p_296471_.events.chunksWhichReceivedNeighbors.iterator();

        while (longiterator.hasNext()) {
            long i = longiterator.nextLong();
            List<SectionRenderDispatcher.RenderSection> list = p_296471_.storage.chunksWaitingForNeighbors.get(i);
            if (list != null && list.get(0).hasAllNeighbors()) {
                p_296471_.events.sectionsToPropagateFrom.addAll(list);
                p_296471_.storage.chunksWaitingForNeighbors.remove(i);
            }
        }

        p_296471_.events.chunksWhichReceivedNeighbors.clear();
    }

    private void addNeighbors(SectionOcclusionGraph.GraphEvents p_295866_, ChunkPos p_295968_) {
        p_295866_.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(p_295968_.x - 1, p_295968_.z));
        p_295866_.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(p_295968_.x, p_295968_.z - 1));
        p_295866_.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(p_295968_.x + 1, p_295968_.z));
        p_295866_.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(p_295968_.x, p_295968_.z + 1));
    }

    private void initializeQueueForFullUpdate(Camera p_295148_, Queue<SectionOcclusionGraph.Node> p_294801_) {
        int i = 16;
        Vec3 vec3 = p_295148_.getPosition();
        BlockPos blockpos = p_295148_.getBlockPosition();
        SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = this.viewArea.getRenderSectionAt(blockpos);
        if (sectionrenderdispatcher$rendersection == null) {
            LevelHeightAccessor levelheightaccessor = this.viewArea.getLevelHeightAccessor();
            boolean flag = blockpos.getY() > levelheightaccessor.getMinBuildHeight();
            int j = flag ? levelheightaccessor.getMaxBuildHeight() - 8 : levelheightaccessor.getMinBuildHeight() + 8;
            int k = Mth.floor(vec3.x / 16.0) * 16;
            int l = Mth.floor(vec3.z / 16.0) * 16;
            int i1 = this.viewArea.getViewDistance();
            List<SectionOcclusionGraph.Node> list = Lists.newArrayList();

            for (int j1 = -i1; j1 <= i1; j1++) {
                for (int k1 = -i1; k1 <= i1; k1++) {
                    SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 = this.viewArea
                        .getRenderSectionAt(new BlockPos(k + SectionPos.sectionToBlockCoord(j1, 8), j, l + SectionPos.sectionToBlockCoord(k1, 8)));
                    if (sectionrenderdispatcher$rendersection1 != null && this.isInViewDistance(blockpos, sectionrenderdispatcher$rendersection1.getOrigin())) {
                        Direction direction = flag ? Direction.DOWN : Direction.UP;
                        SectionOcclusionGraph.Node sectionocclusiongraph$node = new SectionOcclusionGraph.Node(
                            sectionrenderdispatcher$rendersection1, direction, 0
                        );
                        sectionocclusiongraph$node.setDirections(sectionocclusiongraph$node.directions, direction);
                        if (j1 > 0) {
                            sectionocclusiongraph$node.setDirections(sectionocclusiongraph$node.directions, Direction.EAST);
                        } else if (j1 < 0) {
                            sectionocclusiongraph$node.setDirections(sectionocclusiongraph$node.directions, Direction.WEST);
                        }

                        if (k1 > 0) {
                            sectionocclusiongraph$node.setDirections(sectionocclusiongraph$node.directions, Direction.SOUTH);
                        } else if (k1 < 0) {
                            sectionocclusiongraph$node.setDirections(sectionocclusiongraph$node.directions, Direction.NORTH);
                        }

                        list.add(sectionocclusiongraph$node);
                    }
                }
            }

            list.sort(Comparator.comparingDouble(p_294459_ -> blockpos.distSqr(p_294459_.section.getOrigin().offset(8, 8, 8))));
            p_294801_.addAll(list);
        } else {
            p_294801_.add(new SectionOcclusionGraph.Node(sectionrenderdispatcher$rendersection, null, 0));
        }
    }

    private void runUpdates(
        SectionOcclusionGraph.GraphStorage p_295507_,
        Vec3 p_294343_,
        Queue<SectionOcclusionGraph.Node> p_295598_,
        boolean p_295668_,
        Consumer<SectionRenderDispatcher.RenderSection> p_295393_
    ) {
        int i = 16;
        BlockPos blockpos = new BlockPos(Mth.floor(p_294343_.x / 16.0) * 16, Mth.floor(p_294343_.y / 16.0) * 16, Mth.floor(p_294343_.z / 16.0) * 16);
        BlockPos blockpos1 = blockpos.offset(8, 8, 8);

        while (!p_295598_.isEmpty()) {
            SectionOcclusionGraph.Node sectionocclusiongraph$node = p_295598_.poll();
            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection = sectionocclusiongraph$node.section;
            if (p_295507_.renderSections.add(sectionocclusiongraph$node)) {
                p_295393_.accept(sectionocclusiongraph$node.section);
            }

            boolean flag = Math.abs(sectionrenderdispatcher$rendersection.getOrigin().getX() - blockpos.getX()) > 60
                || Math.abs(sectionrenderdispatcher$rendersection.getOrigin().getY() - blockpos.getY()) > 60
                || Math.abs(sectionrenderdispatcher$rendersection.getOrigin().getZ() - blockpos.getZ()) > 60;

            for (Direction direction : DIRECTIONS) {
                SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection1 = this.getRelativeFrom(
                    blockpos, sectionrenderdispatcher$rendersection, direction
                );
                if (sectionrenderdispatcher$rendersection1 != null && (!p_295668_ || !sectionocclusiongraph$node.hasDirection(direction.getOpposite()))) {
                    if (p_295668_ && sectionocclusiongraph$node.hasSourceDirections()) {
                        SectionRenderDispatcher.CompiledSection sectionrenderdispatcher$compiledsection = sectionrenderdispatcher$rendersection.getCompiled();
                        boolean flag1 = false;

                        for (int j = 0; j < DIRECTIONS.length; j++) {
                            if (sectionocclusiongraph$node.hasSourceDirection(j)
                                && sectionrenderdispatcher$compiledsection.facesCanSeeEachother(DIRECTIONS[j].getOpposite(), direction)) {
                                flag1 = true;
                                break;
                            }
                        }

                        if (!flag1) {
                            continue;
                        }
                    }

                    if (p_295668_ && flag) {
                        BlockPos blockpos2 = sectionrenderdispatcher$rendersection1.getOrigin();
                        BlockPos blockpos3 = blockpos2.offset(
                            (direction.getAxis() == Direction.Axis.X ? blockpos1.getX() <= blockpos2.getX() : blockpos1.getX() >= blockpos2.getX()) ? 0 : 16,
                            (direction.getAxis() == Direction.Axis.Y ? blockpos1.getY() <= blockpos2.getY() : blockpos1.getY() >= blockpos2.getY()) ? 0 : 16,
                            (direction.getAxis() == Direction.Axis.Z ? blockpos1.getZ() <= blockpos2.getZ() : blockpos1.getZ() >= blockpos2.getZ()) ? 0 : 16
                        );
                        Vec3 vec31 = new Vec3((double)blockpos3.getX(), (double)blockpos3.getY(), (double)blockpos3.getZ());
                        Vec3 vec3 = p_294343_.subtract(vec31).normalize().scale(CEILED_SECTION_DIAGONAL);
                        boolean flag2 = true;

                        while (p_294343_.subtract(vec31).lengthSqr() > 3600.0) {
                            vec31 = vec31.add(vec3);
                            LevelHeightAccessor levelheightaccessor = this.viewArea.getLevelHeightAccessor();
                            if (vec31.y > (double)levelheightaccessor.getMaxBuildHeight() || vec31.y < (double)levelheightaccessor.getMinBuildHeight()) {
                                break;
                            }

                            SectionRenderDispatcher.RenderSection sectionrenderdispatcher$rendersection2 = this.viewArea
                                .getRenderSectionAt(BlockPos.containing(vec31.x, vec31.y, vec31.z));
                            if (sectionrenderdispatcher$rendersection2 == null
                                || p_295507_.sectionToNodeMap.get(sectionrenderdispatcher$rendersection2) == null) {
                                flag2 = false;
                                break;
                            }
                        }

                        if (!flag2) {
                            continue;
                        }
                    }

                    SectionOcclusionGraph.Node sectionocclusiongraph$node1 = p_295507_.sectionToNodeMap.get(sectionrenderdispatcher$rendersection1);
                    if (sectionocclusiongraph$node1 != null) {
                        sectionocclusiongraph$node1.addSourceDirection(direction);
                    } else {
                        SectionOcclusionGraph.Node sectionocclusiongraph$node2 = new SectionOcclusionGraph.Node(
                            sectionrenderdispatcher$rendersection1, direction, sectionocclusiongraph$node.step + 1
                        );
                        sectionocclusiongraph$node2.setDirections(sectionocclusiongraph$node.directions, direction);
                        if (sectionrenderdispatcher$rendersection1.hasAllNeighbors()) {
                            p_295598_.add(sectionocclusiongraph$node2);
                            p_295507_.sectionToNodeMap.put(sectionrenderdispatcher$rendersection1, sectionocclusiongraph$node2);
                        } else if (this.isInViewDistance(blockpos, sectionrenderdispatcher$rendersection1.getOrigin())) {
                            p_295507_.sectionToNodeMap.put(sectionrenderdispatcher$rendersection1, sectionocclusiongraph$node2);
                            p_295507_.chunksWaitingForNeighbors
                                .computeIfAbsent(ChunkPos.asLong(sectionrenderdispatcher$rendersection1.getOrigin()), p_294377_ -> new ArrayList<>())
                                .add(sectionrenderdispatcher$rendersection1);
                        }
                    }
                }
            }
        }
    }

    private boolean isInViewDistance(BlockPos p_295639_, BlockPos p_295511_) {
        int i = SectionPos.blockToSectionCoord(p_295639_.getX());
        int j = SectionPos.blockToSectionCoord(p_295639_.getZ());
        int k = SectionPos.blockToSectionCoord(p_295511_.getX());
        int l = SectionPos.blockToSectionCoord(p_295511_.getZ());
        return ChunkTrackingView.isInViewDistance(i, j, this.viewArea.getViewDistance(), k, l);
    }

    @Nullable
    private SectionRenderDispatcher.RenderSection getRelativeFrom(BlockPos p_294150_, SectionRenderDispatcher.RenderSection p_295211_, Direction p_294491_) {
        BlockPos blockpos = p_295211_.getRelativeOrigin(p_294491_);
        if (!this.isInViewDistance(p_294150_, blockpos)) {
            return null;
        } else {
            return Mth.abs(p_294150_.getY() - blockpos.getY()) > this.viewArea.getViewDistance() * 16 ? null : this.viewArea.getRenderSectionAt(blockpos);
        }
    }

    @Nullable
    @VisibleForDebug
    protected SectionOcclusionGraph.Node getNode(SectionRenderDispatcher.RenderSection p_296364_) {
        return this.currentGraph.get().storage.sectionToNodeMap.get(p_296364_);
    }

    @OnlyIn(Dist.CLIENT)
    static record GraphEvents(LongSet chunksWhichReceivedNeighbors, BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom) {
        public GraphEvents() {
            this(new LongOpenHashSet(), new LinkedBlockingQueue<>());
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record GraphState(SectionOcclusionGraph.GraphStorage storage, SectionOcclusionGraph.GraphEvents events) {
        public GraphState(int p_295649_) {
            this(new SectionOcclusionGraph.GraphStorage(p_295649_), new SectionOcclusionGraph.GraphEvents());
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class GraphStorage {
        public final SectionOcclusionGraph.SectionToNodeMap sectionToNodeMap;
        public final LinkedHashSet<SectionOcclusionGraph.Node> renderSections;
        public final Long2ObjectMap<List<SectionRenderDispatcher.RenderSection>> chunksWaitingForNeighbors;

        public GraphStorage(int p_296094_) {
            this.sectionToNodeMap = new SectionOcclusionGraph.SectionToNodeMap(p_296094_);
            this.renderSections = new LinkedHashSet<>(p_296094_);
            this.chunksWaitingForNeighbors = new Long2ObjectOpenHashMap<>();
        }
    }

    @OnlyIn(Dist.CLIENT)
    @VisibleForDebug
    protected static class Node {
        @VisibleForDebug
        protected final SectionRenderDispatcher.RenderSection section;
        private byte sourceDirections;
        byte directions;
        @VisibleForDebug
        protected final int step;

        Node(SectionRenderDispatcher.RenderSection p_295110_, @Nullable Direction p_295920_, int p_295951_) {
            this.section = p_295110_;
            if (p_295920_ != null) {
                this.addSourceDirection(p_295920_);
            }

            this.step = p_295951_;
        }

        void setDirections(byte p_295029_, Direction p_296033_) {
            this.directions = (byte)(this.directions | p_295029_ | 1 << p_296033_.ordinal());
        }

        boolean hasDirection(Direction p_294996_) {
            return (this.directions & 1 << p_294996_.ordinal()) > 0;
        }

        void addSourceDirection(Direction p_295444_) {
            this.sourceDirections = (byte)(this.sourceDirections | this.sourceDirections | 1 << p_295444_.ordinal());
        }

        @VisibleForDebug
        protected boolean hasSourceDirection(int p_294302_) {
            return (this.sourceDirections & 1 << p_294302_) > 0;
        }

        boolean hasSourceDirections() {
            return this.sourceDirections != 0;
        }

        @Override
        public int hashCode() {
            return this.section.getOrigin().hashCode();
        }

        @Override
        public boolean equals(Object p_295498_) {
            return !(p_295498_ instanceof SectionOcclusionGraph.Node sectionocclusiongraph$node)
                ? false
                : this.section.getOrigin().equals(sectionocclusiongraph$node.section.getOrigin());
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class SectionToNodeMap {
        private final SectionOcclusionGraph.Node[] nodes;

        SectionToNodeMap(int p_296136_) {
            this.nodes = new SectionOcclusionGraph.Node[p_296136_];
        }

        public void put(SectionRenderDispatcher.RenderSection p_295644_, SectionOcclusionGraph.Node p_295953_) {
            this.nodes[p_295644_.index] = p_295953_;
        }

        @Nullable
        public SectionOcclusionGraph.Node get(SectionRenderDispatcher.RenderSection p_295721_) {
            int i = p_295721_.index;
            return i >= 0 && i < this.nodes.length ? this.nodes[i] : null;
        }
    }
}
