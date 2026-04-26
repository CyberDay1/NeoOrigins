package net.minecraft.client.gui.components;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LoadingDotsWidget extends AbstractWidget {
    private final Font font;

    public LoadingDotsWidget(Font p_295952_, Component p_295297_) {
        super(0, 0, p_295952_.width(p_295297_), 9 * 3, p_295297_);
        this.font = p_295952_;
    }

    @Override
    protected void renderWidget(GuiGraphics p_295359_, int p_296296_, int p_294395_, float p_294654_) {
        int i = this.getX() + this.getWidth() / 2;
        int j = this.getY() + this.getHeight() / 2;
        Component component = this.getMessage();
        p_295359_.drawString(this.font, component, i - this.font.width(component) / 2, j - 9, -1, false);
        String s = LoadingDotsText.get(Util.getMillis());
        p_295359_.drawString(this.font, s, i - this.font.width(s) / 2, j + 9, -8355712, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_294479_) {
    }

    @Override
    public void playDownSound(SoundManager p_305986_) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent p_306162_) {
        return null;
    }
}
