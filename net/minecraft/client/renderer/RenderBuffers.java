package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.SequencedMap;
import net.minecraft.Util;
import net.minecraft.client.resources.model.ModelBakery;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderBuffers {
    private final SectionBufferBuilderPack fixedBufferPack = new SectionBufferBuilderPack();
    private final SectionBufferBuilderPool sectionBufferPool;
    private final MultiBufferSource.BufferSource bufferSource;
    private final MultiBufferSource.BufferSource crumblingBufferSource;
    private final OutlineBufferSource outlineBufferSource;

    public RenderBuffers(int p_307464_) {
        this.sectionBufferPool = SectionBufferBuilderPool.allocate(p_307464_);
        SequencedMap<RenderType, ByteBufferBuilder> sequencedmap = Util.make(new Object2ObjectLinkedOpenHashMap<>(), p_349871_ -> {
            p_349871_.put(Sheets.solidBlockSheet(), this.fixedBufferPack.buffer(RenderType.solid()));
            p_349871_.put(Sheets.cutoutBlockSheet(), this.fixedBufferPack.buffer(RenderType.cutout()));
            p_349871_.put(Sheets.bannerSheet(), this.fixedBufferPack.buffer(RenderType.cutoutMipped()));
            p_349871_.put(Sheets.translucentCullBlockSheet(), this.fixedBufferPack.buffer(RenderType.translucent()));
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, Sheets.shieldSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, Sheets.bedSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, Sheets.shulkerBoxSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, Sheets.signSheet());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, Sheets.hangingSignSheet());
            p_349871_.put(Sheets.chestSheet(), new ByteBufferBuilder(786432));
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.armorEntityGlint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.glint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.glintTranslucent());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.entityGlint());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.entityGlintDirect());
            put((Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder>)p_349871_, RenderType.waterMask());
            ModelBakery.DESTROY_TYPES.forEach(p_173062_ -> put(p_349871_, p_173062_));
        });
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent(sequencedmap));
        this.crumblingBufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        this.bufferSource = MultiBufferSource.immediateWithBuffers(sequencedmap, new ByteBufferBuilder(786432));
        this.outlineBufferSource = new OutlineBufferSource(this.bufferSource);
    }

    private static void put(Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> p_110102_, RenderType p_110103_) {
        p_110102_.put(p_110103_, new ByteBufferBuilder(p_110103_.bufferSize()));
    }

    public SectionBufferBuilderPack fixedBufferPack() {
        return this.fixedBufferPack;
    }

    public SectionBufferBuilderPool sectionBufferPool() {
        return this.sectionBufferPool;
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return this.bufferSource;
    }

    public MultiBufferSource.BufferSource crumblingBufferSource() {
        return this.crumblingBufferSource;
    }

    public OutlineBufferSource outlineBufferSource() {
        return this.outlineBufferSource;
    }
}
