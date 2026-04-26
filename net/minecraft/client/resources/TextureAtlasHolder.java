package net.minecraft.client.resources;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class TextureAtlasHolder implements PreparableReloadListener, AutoCloseable {
    protected final TextureAtlas textureAtlas;
    private final ResourceLocation atlasInfoLocation;
    private final Set<MetadataSectionSerializer<?>> metadataSections;

    public TextureAtlasHolder(TextureManager p_262057_, ResourceLocation p_261554_, ResourceLocation p_262147_) {
        this(p_262057_, p_261554_, p_262147_, SpriteLoader.DEFAULT_METADATA_SECTIONS);
    }

    public TextureAtlasHolder(TextureManager p_294921_, ResourceLocation p_295514_, ResourceLocation p_294920_, Set<MetadataSectionSerializer<?>> p_295003_) {
        this.atlasInfoLocation = p_294920_;
        this.textureAtlas = new TextureAtlas(p_295514_);
        p_294921_.register(this.textureAtlas.location(), this.textureAtlas);
        this.metadataSections = p_295003_;
    }

    protected TextureAtlasSprite getSprite(ResourceLocation p_118902_) {
        return this.textureAtlas.getSprite(p_118902_);
    }

    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier p_249641_,
        ResourceManager p_250036_,
        ProfilerFiller p_249806_,
        ProfilerFiller p_250732_,
        Executor p_249427_,
        Executor p_250510_
    ) {
        return SpriteLoader.create(this.textureAtlas)
            .loadAndStitch(p_250036_, this.atlasInfoLocation, 0, p_249427_, this.metadataSections)
            .thenCompose(SpriteLoader.Preparations::waitForUpload)
            .thenCompose(p_249641_::wait)
            .thenAcceptAsync(p_249246_ -> this.apply(p_249246_, p_250732_), p_250510_);
    }

    private void apply(SpriteLoader.Preparations p_252333_, ProfilerFiller p_250624_) {
        p_250624_.startTick();
        p_250624_.push("upload");
        this.textureAtlas.upload(p_252333_);
        p_250624_.pop();
        p_250624_.endTick();
    }

    @Override
    public void close() {
        this.textureAtlas.clearTextureData();
    }
}
