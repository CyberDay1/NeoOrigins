package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PingDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int CHART_TOP_VALUE = 500;

    public PingDebugChart(Font p_298944_, SampleStorage p_323605_) {
        super(p_298944_, p_323605_);
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics p_299050_, int p_298600_, int p_298302_, int p_298207_) {
        this.drawStringWithShade(p_299050_, "500 ms", p_298600_ + 1, p_298207_ - 60 + 1);
    }

    @Override
    protected String toDisplayString(double p_298261_) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(p_298261_));
    }

    @Override
    protected int getSampleHeight(double p_298980_) {
        return (int)Math.round(p_298980_ * 60.0 / 500.0);
    }

    @Override
    protected int getSampleColor(long p_298402_) {
        return this.getSampleColor((double)p_298402_, 0.0, -16711936, 250.0, -256, 500.0, -65536);
    }
}
