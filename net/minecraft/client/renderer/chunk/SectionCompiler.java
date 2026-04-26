package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SectionCompiler {
    private final BlockRenderDispatcher blockRenderer;
    private final BlockEntityRenderDispatcher blockEntityRenderer;

    public SectionCompiler(BlockRenderDispatcher p_350768_, BlockEntityRenderDispatcher p_350715_) {
        this.blockRenderer = p_350768_;
        this.blockEntityRenderer = p_350715_;
    }

    public SectionCompiler.Results compile(SectionPos p_350790_, RenderChunkRegion p_350782_, VertexSorting p_350635_, SectionBufferBuilderPack p_350612_) {
        return compile(p_350790_, p_350782_, p_350635_, p_350612_, List.of());
    }

    public SectionCompiler.Results compile(SectionPos p_350790_, RenderChunkRegion p_350782_, VertexSorting p_350635_, SectionBufferBuilderPack p_350612_, List<net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers) {
        SectionCompiler.Results sectioncompiler$results = new SectionCompiler.Results();
        BlockPos blockpos = p_350790_.origin();
        BlockPos blockpos1 = blockpos.offset(15, 15, 15);
        VisGraph visgraph = new VisGraph();
        PoseStack posestack = new PoseStack();
        ModelBlockRenderer.enableCaching();
        Map<RenderType, BufferBuilder> map = new Reference2ObjectArrayMap<>(RenderType.chunkBufferLayers().size());
        RandomSource randomsource = RandomSource.create();

        for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos, blockpos1)) {
            BlockState blockstate = p_350782_.getBlockState(blockpos2);
            if (blockstate.isSolidRender(p_350782_, blockpos2)) {
                visgraph.setOpaque(blockpos2);
            }

            if (blockstate.hasBlockEntity()) {
                BlockEntity blockentity = p_350782_.getBlockEntity(blockpos2);
                if (blockentity != null) {
                    this.handleBlockEntity(sectioncompiler$results, blockentity);
                }
            }

            FluidState fluidstate = blockstate.getFluidState();
            if (!fluidstate.isEmpty()) {
                RenderType rendertype = ItemBlockRenderTypes.getRenderLayer(fluidstate);
                BufferBuilder bufferbuilder = this.getOrBeginLayer(map, p_350612_, rendertype);
                this.blockRenderer.renderLiquid(blockpos2, p_350782_, bufferbuilder, blockstate, fluidstate);
            }

            if (blockstate.getRenderShape() == RenderShape.MODEL) {
                var model = this.blockRenderer.getBlockModel(blockstate);
                var modelData = p_350782_.getModelData(blockpos2);
                modelData = model.getModelData(p_350782_, blockpos2, blockstate, modelData);
                randomsource.setSeed(blockstate.getSeed(blockpos2));
                for (RenderType rendertype2 : model.getRenderTypes(blockstate, randomsource, modelData)) {
                BufferBuilder bufferbuilder1 = this.getOrBeginLayer(map, p_350612_, rendertype2);
                posestack.pushPose();
                posestack.translate(
                    (float)SectionPos.sectionRelative(blockpos2.getX()),
                    (float)SectionPos.sectionRelative(blockpos2.getY()),
                    (float)SectionPos.sectionRelative(blockpos2.getZ())
                );
                this.blockRenderer.renderBatched(blockstate, blockpos2, p_350782_, posestack, bufferbuilder1, true, randomsource, modelData, rendertype2);
                posestack.popPose();
                }
            }
        }
        net.neoforged.neoforge.client.ClientHooks.addAdditionalGeometry(
                additionalRenderers,
                type -> this.getOrBeginLayer(map, p_350612_, type),
                p_350782_,
                posestack
        );
        for (Entry<RenderType, BufferBuilder> entry : map.entrySet()) {
            RenderType rendertype1 = entry.getKey();
            MeshData meshdata = entry.getValue().build();
            if (meshdata != null) {
                if (rendertype1 == RenderType.translucent()) {
                    sectioncompiler$results.transparencyState = meshdata.sortQuads(p_350612_.buffer(RenderType.translucent()), p_350635_);
                }

                sectioncompiler$results.renderedLayers.put(rendertype1, meshdata);
            }
        }

        ModelBlockRenderer.clearCache();
        sectioncompiler$results.visibilitySet = visgraph.resolve();
        return sectioncompiler$results;
    }

    private BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> p_350651_, SectionBufferBuilderPack p_350965_, RenderType p_350494_) {
        BufferBuilder bufferbuilder = p_350651_.get(p_350494_);
        if (bufferbuilder == null) {
            ByteBufferBuilder bytebufferbuilder = p_350965_.buffer(p_350494_);
            bufferbuilder = new BufferBuilder(bytebufferbuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            p_350651_.put(p_350494_, bufferbuilder);
        }

        return bufferbuilder;
    }

    private <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results p_350754_, E p_350386_) {
        BlockEntityRenderer<E> blockentityrenderer = this.blockEntityRenderer.getRenderer(p_350386_);
        if (blockentityrenderer != null) {
            if (blockentityrenderer.shouldRenderOffScreen(p_350386_)) {
                p_350754_.globalBlockEntities.add(p_350386_);
            } else {
                p_350754_.blockEntities.add(p_350386_); // Neo: Fix MC-112730
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static final class Results {
        public final List<BlockEntity> globalBlockEntities = new ArrayList<>();
        public final List<BlockEntity> blockEntities = new ArrayList<>();
        public final Map<RenderType, MeshData> renderedLayers = new Reference2ObjectArrayMap<>();
        public VisibilitySet visibilitySet = new VisibilitySet();
        @Nullable
        public MeshData.SortState transparencyState;

        public void release() {
            this.renderedLayers.values().forEach(MeshData::close);
        }
    }
}
