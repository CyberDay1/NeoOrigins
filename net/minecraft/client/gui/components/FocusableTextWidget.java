package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FocusableTextWidget extends MultiLineTextWidget {
    private static final int DEFAULT_PADDING = 4;
    private final boolean alwaysShowBorder;
    private final int padding;

    public FocusableTextWidget(int p_295441_, Component p_296440_, Font p_296307_) {
        this(p_295441_, p_296440_, p_296307_, 4);
    }

    public FocusableTextWidget(int p_295671_, Component p_295867_, Font p_294548_, int p_330770_) {
        this(p_295671_, p_295867_, p_294548_, true, p_330770_);
    }

    public FocusableTextWidget(int p_331308_, Component p_330271_, Font p_330898_, boolean p_332016_, int p_331228_) {
        super(p_330271_, p_330898_);
        this.setMaxWidth(p_331308_);
        this.setCentered(true);
        this.active = true;
        this.alwaysShowBorder = p_332016_;
        this.padding = p_331228_;
    }

    public void containWithin(int p_331002_) {
        this.setMaxWidth(p_331002_ - this.padding * 4);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_295798_) {
        p_295798_.add(NarratedElementType.TITLE, this.getMessage());
    }

    @Override
    public void renderWidget(GuiGraphics p_296375_, int p_295686_, int p_295354_, float p_295563_) {
        if (this.isFocused() || this.alwaysShowBorder) {
            int i = this.getX() - this.padding;
            int j = this.getY() - this.padding;
            int k = this.getWidth() + this.padding * 2;
            int l = this.getHeight() + this.padding * 2;
            int i1 = this.alwaysShowBorder ? (this.isFocused() ? -1 : -6250336) : -1;
            p_296375_.fill(i + 1, j, i + k, j + l, -16777216);
            p_296375_.renderOutline(i, j, k, l, i1);
        }

        super.renderWidget(p_296375_, p_295686_, p_295354_, p_295563_);
    }

    @Override
    public void playDownSound(SoundManager p_295576_) {
    }
}
