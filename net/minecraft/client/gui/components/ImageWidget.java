package net.minecraft.client.gui.components;

import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ImageWidget extends AbstractWidget {
    ImageWidget(int p_275421_, int p_275294_, int p_275403_, int p_275631_) {
        super(p_275421_, p_275294_, p_275403_, p_275631_, CommonComponents.EMPTY);
    }

    public static ImageWidget texture(int p_294719_, int p_294578_, ResourceLocation p_295560_, int p_295321_, int p_296474_) {
        return new ImageWidget.Texture(0, 0, p_294719_, p_294578_, p_295560_, p_295321_, p_296474_);
    }

    public static ImageWidget sprite(int p_295244_, int p_296002_, ResourceLocation p_295131_) {
        return new ImageWidget.Sprite(0, 0, p_295244_, p_296002_, p_295131_);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_275454_) {
    }

    @Override
    public void playDownSound(SoundManager p_295108_) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent p_296129_) {
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    static class Sprite extends ImageWidget {
        private final ResourceLocation sprite;

        public Sprite(int p_295957_, int p_294219_, int p_296365_, int p_294849_, ResourceLocation p_296297_) {
            super(p_295957_, p_294219_, p_296365_, p_294849_);
            this.sprite = p_296297_;
        }

        @Override
        public void renderWidget(GuiGraphics p_295869_, int p_295287_, int p_294110_, float p_296031_) {
            p_295869_.blitSprite(this.sprite, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Texture extends ImageWidget {
        private final ResourceLocation texture;
        private final int textureWidth;
        private final int textureHeight;

        public Texture(int p_294324_, int p_296206_, int p_294604_, int p_294607_, ResourceLocation p_294536_, int p_295196_, int p_294112_) {
            super(p_294324_, p_296206_, p_294604_, p_294607_);
            this.texture = p_294536_;
            this.textureWidth = p_295196_;
            this.textureHeight = p_294112_;
        }

        @Override
        protected void renderWidget(GuiGraphics p_294145_, int p_294755_, int p_294985_, float p_294245_) {
            p_294145_.blit(
                this.texture,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight(),
                0.0F,
                0.0F,
                this.getWidth(),
                this.getHeight(),
                this.textureWidth,
                this.textureHeight
            );
        }
    }
}
