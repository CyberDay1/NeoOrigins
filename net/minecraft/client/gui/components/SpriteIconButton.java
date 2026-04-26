package net.minecraft.client.gui.components;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class SpriteIconButton extends Button {
    protected final ResourceLocation sprite;
    protected final int spriteWidth;
    protected final int spriteHeight;

    SpriteIconButton(
        int p_294218_,
        int p_294461_,
        Component p_294760_,
        int p_296342_,
        int p_296291_,
        ResourceLocation p_294131_,
        Button.OnPress p_295667_,
        @Nullable Button.CreateNarration p_332100_
    ) {
        super(0, 0, p_294218_, p_294461_, p_294760_, p_295667_, p_332100_ == null ? DEFAULT_NARRATION : p_332100_);
        this.spriteWidth = p_296342_;
        this.spriteHeight = p_296291_;
        this.sprite = p_294131_;
    }

    public static SpriteIconButton.Builder builder(Component p_294639_, Button.OnPress p_295155_, boolean p_295622_) {
        return new SpriteIconButton.Builder(p_294639_, p_295155_, p_295622_);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private final boolean iconOnly;
        private int width = 150;
        private int height = 20;
        @Nullable
        private ResourceLocation sprite;
        private int spriteWidth;
        private int spriteHeight;
        @Nullable
        Button.CreateNarration narration;

        public Builder(Component p_294568_, Button.OnPress p_294520_, boolean p_294559_) {
            this.message = p_294568_;
            this.onPress = p_294520_;
            this.iconOnly = p_294559_;
        }

        public SpriteIconButton.Builder width(int p_295136_) {
            this.width = p_295136_;
            return this;
        }

        public SpriteIconButton.Builder size(int p_295812_, int p_296135_) {
            this.width = p_295812_;
            this.height = p_296135_;
            return this;
        }

        public SpriteIconButton.Builder sprite(ResourceLocation p_295718_, int p_296046_, int p_295188_) {
            this.sprite = p_295718_;
            this.spriteWidth = p_296046_;
            this.spriteHeight = p_295188_;
            return this;
        }

        public SpriteIconButton.Builder narration(Button.CreateNarration p_331295_) {
            this.narration = p_331295_;
            return this;
        }

        public SpriteIconButton build() {
            if (this.sprite == null) {
                throw new IllegalStateException("Sprite not set");
            } else {
                return (SpriteIconButton)(this.iconOnly
                    ? new SpriteIconButton.CenteredIcon(
                        this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.narration
                    )
                    : new SpriteIconButton.TextAndIcon(
                        this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.narration
                    ));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class CenteredIcon extends SpriteIconButton {
        protected CenteredIcon(
            int p_295914_,
            int p_294852_,
            Component p_295609_,
            int p_294922_,
            int p_296462_,
            ResourceLocation p_295554_,
            Button.OnPress p_294427_,
            @Nullable Button.CreateNarration p_330653_
        ) {
            super(p_295914_, p_294852_, p_295609_, p_294922_, p_296462_, p_295554_, p_294427_, p_330653_);
        }

        @Override
        public void renderWidget(GuiGraphics p_295402_, int p_295733_, int p_294839_, float p_296191_) {
            super.renderWidget(p_295402_, p_295733_, p_294839_, p_296191_);
            int i = this.getX() + this.getWidth() / 2 - this.spriteWidth / 2;
            int j = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
            p_295402_.blitSprite(this.sprite, i, j, this.spriteWidth, this.spriteHeight);
        }

        @Override
        public void renderString(GuiGraphics p_294683_, Font p_295870_, int p_295770_) {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class TextAndIcon extends SpriteIconButton {
        protected TextAndIcon(
            int p_296442_,
            int p_294340_,
            Component p_296265_,
            int p_294900_,
            int p_295900_,
            ResourceLocation p_296097_,
            Button.OnPress p_295566_,
            @Nullable Button.CreateNarration p_330735_
        ) {
            super(p_296442_, p_294340_, p_296265_, p_294900_, p_295900_, p_296097_, p_295566_, p_330735_);
        }

        @Override
        public void renderWidget(GuiGraphics p_294156_, int p_295818_, int p_294994_, float p_296436_) {
            super.renderWidget(p_294156_, p_295818_, p_294994_, p_296436_);
            int i = this.getX() + this.getWidth() - this.spriteWidth - 2;
            int j = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
            p_294156_.blitSprite(this.sprite, i, j, this.spriteWidth, this.spriteHeight);
        }

        @Override
        public void renderString(GuiGraphics p_296211_, Font p_295107_, int p_295081_) {
            int i = this.getX() + 2;
            int j = this.getX() + this.getWidth() - this.spriteWidth - 4;
            int k = this.getX() + this.getWidth() / 2;
            renderScrollingString(p_296211_, p_295107_, this.getMessage(), k, i, this.getY(), j, this.getY() + this.getHeight(), p_295081_);
        }
    }
}
