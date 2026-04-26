package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

@OnlyIn(Dist.CLIENT)
public class CubeMap {
    private static final int SIDES = 6;
    private final ResourceLocation[] images = new ResourceLocation[6];

    public CubeMap(ResourceLocation p_108848_) {
        for (int i = 0; i < 6; i++) {
            this.images[i] = p_108848_.withPath(p_108848_.getPath() + "_" + i + ".png");
        }
    }

    public void render(Minecraft p_108850_, float p_108851_, float p_108852_, float p_108853_) {
        Tesselator tesselator = Tesselator.getInstance();
        Matrix4f matrix4f = new Matrix4f()
            .setPerspective(1.4835298F, (float)p_108850_.getWindow().getWidth() / (float)p_108850_.getWindow().getHeight(), 0.05F, 10.0F);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushMatrix();
        matrix4fstack.rotationX((float) Math.PI);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        int i = 2;

        for (int j = 0; j < 4; j++) {
            matrix4fstack.pushMatrix();
            float f = ((float)(j % 2) / 2.0F - 0.5F) / 256.0F;
            float f1 = ((float)(j / 2) / 2.0F - 0.5F) / 256.0F;
            float f2 = 0.0F;
            matrix4fstack.translate(f, f1, 0.0F);
            matrix4fstack.rotateX(p_108851_ * (float) (Math.PI / 180.0));
            matrix4fstack.rotateY(p_108852_ * (float) (Math.PI / 180.0));
            RenderSystem.applyModelViewMatrix();

            for (int k = 0; k < 6; k++) {
                RenderSystem.setShaderTexture(0, this.images[k]);
                BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                int l = Math.round(255.0F * p_108853_) / (j + 1);
                if (k == 0) {
                    bufferbuilder.addVertex(-1.0F, -1.0F, 1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, 1.0F, 1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, 1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, -1.0F, 1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                if (k == 1) {
                    bufferbuilder.addVertex(1.0F, -1.0F, 1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, 1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, -1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, -1.0F, -1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                if (k == 2) {
                    bufferbuilder.addVertex(1.0F, -1.0F, -1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, -1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, 1.0F, -1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, -1.0F, -1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                if (k == 3) {
                    bufferbuilder.addVertex(-1.0F, -1.0F, -1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, 1.0F, -1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, 1.0F, 1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, -1.0F, 1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                if (k == 4) {
                    bufferbuilder.addVertex(-1.0F, -1.0F, -1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, -1.0F, 1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, -1.0F, 1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, -1.0F, -1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                if (k == 5) {
                    bufferbuilder.addVertex(-1.0F, 1.0F, 1.0F).setUv(0.0F, 0.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(-1.0F, 1.0F, -1.0F).setUv(0.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, -1.0F).setUv(1.0F, 1.0F).setWhiteAlpha(l);
                    bufferbuilder.addVertex(1.0F, 1.0F, 1.0F).setUv(1.0F, 0.0F).setWhiteAlpha(l);
                }

                BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            }

            matrix4fstack.popMatrix();
            RenderSystem.colorMask(true, true, true, false);
        }

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.restoreProjectionMatrix();
        matrix4fstack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    public CompletableFuture<Void> preload(TextureManager p_108855_, Executor p_108856_) {
        CompletableFuture<?>[] completablefuture = new CompletableFuture[6];

        for (int i = 0; i < completablefuture.length; i++) {
            completablefuture[i] = p_108855_.preload(this.images[i], p_108856_);
        }

        return CompletableFuture.allOf(completablefuture);
    }
}
