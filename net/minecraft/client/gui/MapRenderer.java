package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class MapRenderer implements AutoCloseable {
    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;
    final TextureManager textureManager;
    final MapDecorationTextureManager decorationTextures;
    private final Int2ObjectMap<MapRenderer.MapInstance> maps = new Int2ObjectOpenHashMap<>();

    public MapRenderer(TextureManager p_93259_, MapDecorationTextureManager p_335870_) {
        this.textureManager = p_93259_;
        this.decorationTextures = p_335870_;
    }

    public void update(MapId p_323687_, MapItemSavedData p_168767_) {
        this.getOrCreateMapInstance(p_323687_, p_168767_).forceUpload();
    }

    public void render(PoseStack p_168772_, MultiBufferSource p_168773_, MapId p_324127_, MapItemSavedData p_168775_, boolean p_168776_, int p_168774_) {
        this.getOrCreateMapInstance(p_324127_, p_168775_).draw(p_168772_, p_168773_, p_168776_, p_168774_);
    }

    private MapRenderer.MapInstance getOrCreateMapInstance(MapId p_323495_, MapItemSavedData p_168780_) {
        return this.maps.compute(p_323495_.id(), (p_182563_, p_182564_) -> {
            if (p_182564_ == null) {
                return new MapRenderer.MapInstance(p_182563_, p_168780_);
            } else {
                p_182564_.replaceMapData(p_168780_);
                return (MapRenderer.MapInstance)p_182564_;
            }
        });
    }

    public void resetData() {
        for (MapRenderer.MapInstance maprenderer$mapinstance : this.maps.values()) {
            maprenderer$mapinstance.close();
        }

        this.maps.clear();
    }

    @Override
    public void close() {
        this.resetData();
    }

    @OnlyIn(Dist.CLIENT)
    class MapInstance implements AutoCloseable {
        private MapItemSavedData data;
        private final DynamicTexture texture;
        private final RenderType renderType;
        private boolean requiresUpload = true;

        MapInstance(int p_168783_, MapItemSavedData p_168784_) {
            this.data = p_168784_;
            this.texture = new DynamicTexture(128, 128, true);
            ResourceLocation resourcelocation = MapRenderer.this.textureManager.register("map/" + p_168783_, this.texture);
            this.renderType = RenderType.text(resourcelocation);
        }

        void replaceMapData(MapItemSavedData p_182568_) {
            boolean flag = this.data != p_182568_;
            this.data = p_182568_;
            this.requiresUpload |= flag;
        }

        public void forceUpload() {
            this.requiresUpload = true;
        }

        private void updateTexture() {
            for (int i = 0; i < 128; i++) {
                for (int j = 0; j < 128; j++) {
                    int k = j + i * 128;
                    this.texture.getPixels().setPixelRGBA(j, i, MapColor.getColorFromPackedId(this.data.colors[k]));
                }
            }

            this.texture.upload();
        }

        void draw(PoseStack p_93292_, MultiBufferSource p_93293_, boolean p_93294_, int p_93295_) {
            if (this.requiresUpload) {
                this.updateTexture();
                this.requiresUpload = false;
            }

            int i = 0;
            int j = 0;
            float f = 0.0F;
            Matrix4f matrix4f = p_93292_.last().pose();
            VertexConsumer vertexconsumer = p_93293_.getBuffer(this.renderType);
            vertexconsumer.addVertex(matrix4f, 0.0F, 128.0F, -0.01F).setColor(-1).setUv(0.0F, 1.0F).setLight(p_93295_);
            vertexconsumer.addVertex(matrix4f, 128.0F, 128.0F, -0.01F).setColor(-1).setUv(1.0F, 1.0F).setLight(p_93295_);
            vertexconsumer.addVertex(matrix4f, 128.0F, 0.0F, -0.01F).setColor(-1).setUv(1.0F, 0.0F).setLight(p_93295_);
            vertexconsumer.addVertex(matrix4f, 0.0F, 0.0F, -0.01F).setColor(-1).setUv(0.0F, 0.0F).setLight(p_93295_);
            int k = 0;

            for (MapDecoration mapdecoration : this.data.getDecorations()) {
                if (!p_93294_ || mapdecoration.renderOnFrame()) {
                    if (net.neoforged.neoforge.client.gui.map.MapDecorationRendererManager.render(mapdecoration, p_93292_, p_93293_, data, MapRenderer.this.decorationTextures, p_93294_, p_93295_, k)) {
                        k++;
                        continue;
                    }
                    p_93292_.pushPose();
                    p_93292_.translate(0.0F + (float)mapdecoration.x() / 2.0F + 64.0F, 0.0F + (float)mapdecoration.y() / 2.0F + 64.0F, -0.02F);
                    p_93292_.mulPose(Axis.ZP.rotationDegrees((float)(mapdecoration.rot() * 360) / 16.0F));
                    p_93292_.scale(4.0F, 4.0F, 3.0F);
                    p_93292_.translate(-0.125F, 0.125F, 0.0F);
                    Matrix4f matrix4f1 = p_93292_.last().pose();
                    float f1 = -0.001F;
                    TextureAtlasSprite textureatlassprite = MapRenderer.this.decorationTextures.get(mapdecoration);
                    float f2 = textureatlassprite.getU0();
                    float f3 = textureatlassprite.getV0();
                    float f4 = textureatlassprite.getU1();
                    float f5 = textureatlassprite.getV1();
                    VertexConsumer vertexconsumer1 = p_93293_.getBuffer(RenderType.text(textureatlassprite.atlasLocation()));
                    vertexconsumer1.addVertex(matrix4f1, -1.0F, 1.0F, (float)k * -0.001F).setColor(-1).setUv(f2, f3).setLight(p_93295_);
                    vertexconsumer1.addVertex(matrix4f1, 1.0F, 1.0F, (float)k * -0.001F).setColor(-1).setUv(f4, f3).setLight(p_93295_);
                    vertexconsumer1.addVertex(matrix4f1, 1.0F, -1.0F, (float)k * -0.001F).setColor(-1).setUv(f4, f5).setLight(p_93295_);
                    vertexconsumer1.addVertex(matrix4f1, -1.0F, -1.0F, (float)k * -0.001F).setColor(-1).setUv(f2, f5).setLight(p_93295_);
                    p_93292_.popPose();
                    if (mapdecoration.name().isPresent()) {
                        Font font = Minecraft.getInstance().font;
                        Component component = mapdecoration.name().get();
                        float f6 = (float)font.width(component);
                        float f7 = Mth.clamp(25.0F / f6, 0.0F, 6.0F / 9.0F);
                        p_93292_.pushPose();
                        p_93292_.translate(
                            0.0F + (float)mapdecoration.x() / 2.0F + 64.0F - f6 * f7 / 2.0F, 0.0F + (float)mapdecoration.y() / 2.0F + 64.0F + 4.0F, -0.025F
                        );
                        p_93292_.scale(f7, f7, 1.0F);
                        p_93292_.translate(0.0F, 0.0F, -0.1F);
                        font.drawInBatch(
                            component, 0.0F, 0.0F, -1, false, p_93292_.last().pose(), p_93293_, Font.DisplayMode.NORMAL, Integer.MIN_VALUE, p_93295_
                        );
                        p_93292_.popPose();
                    }

                    k++;
                }
            }
        }

        @Override
        public void close() {
            this.texture.close();
        }
    }
}
