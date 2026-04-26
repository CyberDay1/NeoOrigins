package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FpsDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int CHART_TOP_FPS = 30;
    private static final double CHART_TOP_VALUE = 33.333333333333336;

    public FpsDebugChart(Font p_298374_, SampleStorage p_323606_) {
        super(p_298374_, p_323606_);
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics p_298713_, int p_298427_, int p_299203_, int p_298951_) {
        this.drawStringWithShade(p_298713_, "30 FPS", p_298427_ + 1, p_298951_ - 60 + 1);
        this.drawStringWithShade(p_298713_, "60 FPS", p_298427_ + 1, p_298951_ - 30 + 1);
        p_298713_.hLine(RenderType.guiOverlay(), p_298427_, p_298427_ + p_299203_ - 1, p_298951_ - 30, -1);
        int i = Minecraft.getInstance().options.framerateLimit().get();
        if (i > 0 && i <= 250) {
            p_298713_.hLine(RenderType.guiOverlay(), p_298427_, p_298427_ + p_299203_ - 1, p_298951_ - this.getSampleHeight(1.0E9 / (double)i) - 1, -16711681);
        }
    }

    @Override
    protected String toDisplayString(double p_298621_) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(toMilliseconds(p_298621_)));
    }

    @Override
    protected int getSampleHeight(double p_298242_) {
        return (int)Math.round(toMilliseconds(p_298242_) * 60.0 / 33.333333333333336);
    }

    @Override
    protected int getSampleColor(long p_298344_) {
        return this.getSampleColor(toMilliseconds((double)p_298344_), 0.0, -16711936, 28.0, -256, 56.0, -65536);
    }

    private static double toMilliseconds(double p_298493_) {
        return p_298493_ / 1000000.0;
    }
}
