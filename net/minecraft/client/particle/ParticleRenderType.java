package net.minecraft.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ParticleRenderType {
    ParticleRenderType TERRAIN_SHEET = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator p_350993_, TextureManager p_107442_) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            return p_350993_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "TERRAIN_SHEET";
        }
    };
    ParticleRenderType PARTICLE_SHEET_OPAQUE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator p_350576_, TextureManager p_107449_) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return p_350576_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "PARTICLE_SHEET_OPAQUE";
        }

        @Override
        public boolean isTranslucent() {
            return false;
        }
    };
    ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator p_350826_, TextureManager p_107456_) {
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return p_350826_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "PARTICLE_SHEET_TRANSLUCENT";
        }
    };
    ParticleRenderType PARTICLE_SHEET_LIT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator p_351047_, TextureManager p_107463_) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            return p_351047_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "PARTICLE_SHEET_LIT";
        }

        @Override
        public boolean isTranslucent() {
            return false;
        }
    };
    ParticleRenderType CUSTOM = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator p_350910_, TextureManager p_107470_) {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return p_350910_.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "CUSTOM";
        }
    };
    ParticleRenderType NO_RENDER = new ParticleRenderType() {
        @Nullable
        @Override
        public BufferBuilder begin(Tesselator p_350814_, TextureManager p_107477_) {
            return null;
        }

        @Override
        public String toString() {
            return "NO_RENDER";
        }
    };

    @Nullable
    BufferBuilder begin(Tesselator p_350949_, TextureManager p_107437_);

    /** {@return whether this type renders before or after the translucent chunk layer} */
    default boolean isTranslucent() {
        return true;
    }
}
