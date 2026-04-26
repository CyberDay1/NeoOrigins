package net.minecraft.client.gui.screens;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GenericMessageScreen extends Screen {
    @Nullable
    private FocusableTextWidget textWidget;

    public GenericMessageScreen(Component p_331916_) {
        super(p_331916_);
    }

    @Override
    protected void init() {
        this.textWidget = this.addRenderableWidget(new FocusableTextWidget(this.width, this.title, this.font, 12));
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.textWidget != null) {
            this.textWidget.containWithin(this.width);
            this.textWidget.setPosition(this.width / 2 - this.textWidget.getWidth() / 2, this.height / 2 - 9 / 2);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics p_330526_, int p_330256_, int p_331601_, float p_331163_) {
        this.renderPanorama(p_330526_, p_331163_);
        this.renderBlurredBackground(p_331163_);
        this.renderMenuBackground(p_330526_);
    }
}
