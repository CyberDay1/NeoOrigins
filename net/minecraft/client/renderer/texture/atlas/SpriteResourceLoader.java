package net.minecraft.client.renderer.texture.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface SpriteResourceLoader {
    Logger LOGGER = LogUtils.getLogger();

    static SpriteResourceLoader create(Collection<MetadataSectionSerializer<?>> p_296204_) {
        return (p_293680_, p_293681_, constructor) -> {
            ResourceMetadata resourcemetadata;
            try {
                resourcemetadata = p_293681_.metadata().copySections(p_296204_);
            } catch (Exception exception) {
                LOGGER.error("Unable to parse metadata from {}", p_293680_, exception);
                return null;
            }

            NativeImage nativeimage;
            try (InputStream inputstream = p_293681_.open()) {
                nativeimage = NativeImage.read(inputstream);
            } catch (IOException ioexception) {
                LOGGER.error("Using missing texture, unable to load {}", p_293680_, ioexception);
                return null;
            }

            AnimationMetadataSection animationmetadatasection = resourcemetadata.getSection(AnimationMetadataSection.SERIALIZER)
                .orElse(AnimationMetadataSection.EMPTY);
            FrameSize framesize = animationmetadatasection.calculateFrameSize(nativeimage.getWidth(), nativeimage.getHeight());
            if (Mth.isMultipleOf(nativeimage.getWidth(), framesize.width()) && Mth.isMultipleOf(nativeimage.getHeight(), framesize.height())) {
                return constructor.create(p_293680_, framesize, nativeimage, resourcemetadata);
            } else {
                LOGGER.error(
                    "Image {} size {},{} is not multiple of frame size {},{}",
                    p_293680_,
                    nativeimage.getWidth(),
                    nativeimage.getHeight(),
                    framesize.width(),
                    framesize.height()
                );
                nativeimage.close();
                return null;
            }
        };
    }

    @Nullable
    default SpriteContents loadSprite(ResourceLocation p_295581_, Resource p_294329_) {
        return loadSprite(p_295581_, p_294329_, SpriteContents::new);
    }

    @Nullable
    SpriteContents loadSprite(ResourceLocation id, Resource resource, net.neoforged.neoforge.client.textures.SpriteContentsConstructor constructor);
}
