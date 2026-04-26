package net.minecraft.client.gui.components.debugchart;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractDebugChart {
    protected static final int COLOR_GREY = 14737632;
    protected static final int CHART_HEIGHT = 60;
    protected static final int LINE_WIDTH = 1;
    protected final Font font;
    protected final SampleStorage sampleStorage;

    protected AbstractDebugChart(Font p_299029_, SampleStorage p_324391_) {
        this.font = p_299029_;
        this.sampleStorage = p_324391_;
    }

    public int getWidth(int p_298843_) {
        return Math.min(this.sampleStorage.capacity() + 2, p_298843_);
    }

    public void drawChart(GuiGraphics p_298960_, int p_298986_, int p_298301_) {
        int i = p_298960_.guiHeight();
        p_298960_.fill(RenderType.guiOverlay(), p_298986_, i - 60, p_298986_ + p_298301_, i, -1873784752);
        long j = 0L;
        long k = 2147483647L;
        long l = -2147483648L;
        int i1 = Math.max(0, this.sampleStorage.capacity() - (p_298301_ - 2));
        int j1 = this.sampleStorage.size() - i1;

        for (int k1 = 0; k1 < j1; k1++) {
            int l1 = p_298986_ + k1 + 1;
            int i2 = i1 + k1;
            long j2 = this.getValueForAggregation(i2);
            k = Math.min(k, j2);
            l = Math.max(l, j2);
            j += j2;
            this.drawDimensions(p_298960_, i, l1, i2);
        }

        p_298960_.hLine(RenderType.guiOverlay(), p_298986_, p_298986_ + p_298301_ - 1, i - 60, -1);
        p_298960_.hLine(RenderType.guiOverlay(), p_298986_, p_298986_ + p_298301_ - 1, i - 1, -1);
        p_298960_.vLine(RenderType.guiOverlay(), p_298986_, i - 60, i, -1);
        p_298960_.vLine(RenderType.guiOverlay(), p_298986_ + p_298301_ - 1, i - 60, i, -1);
        if (j1 > 0) {
            String s = this.toDisplayString((double)k) + " min";
            String s1 = this.toDisplayString((double)j / (double)j1) + " avg";
            String s2 = this.toDisplayString((double)l) + " max";
            p_298960_.drawString(this.font, s, p_298986_ + 2, i - 60 - 9, 14737632);
            p_298960_.drawCenteredString(this.font, s1, p_298986_ + p_298301_ / 2, i - 60 - 9, 14737632);
            p_298960_.drawString(this.font, s2, p_298986_ + p_298301_ - this.font.width(s2) - 2, i - 60 - 9, 14737632);
        }

        this.renderAdditionalLinesAndLabels(p_298960_, p_298986_, p_298301_, i);
    }

    protected void drawDimensions(GuiGraphics p_321561_, int p_321861_, int p_321591_, int p_321654_) {
        this.drawMainDimension(p_321561_, p_321861_, p_321591_, p_321654_);
        this.drawAdditionalDimensions(p_321561_, p_321861_, p_321591_, p_321654_);
    }

    protected void drawMainDimension(GuiGraphics p_321499_, int p_321849_, int p_321568_, int p_321766_) {
        long i = this.sampleStorage.get(p_321766_);
        int j = this.getSampleHeight((double)i);
        int k = this.getSampleColor(i);
        p_321499_.fill(RenderType.guiOverlay(), p_321568_, p_321849_ - j, p_321568_ + 1, p_321849_, k);
    }

    protected void drawAdditionalDimensions(GuiGraphics p_321486_, int p_321516_, int p_321827_, int p_321819_) {
    }

    protected long getValueForAggregation(int p_321706_) {
        return this.sampleStorage.get(p_321706_);
    }

    protected void renderAdditionalLinesAndLabels(GuiGraphics p_298895_, int p_298979_, int p_298732_, int p_299176_) {
    }

    protected void drawStringWithShade(GuiGraphics p_298386_, String p_298809_, int p_298657_, int p_298698_) {
        p_298386_.fill(RenderType.guiOverlay(), p_298657_, p_298698_, p_298657_ + this.font.width(p_298809_) + 1, p_298698_ + 9, -1873784752);
        p_298386_.drawString(this.font, p_298809_, p_298657_ + 1, p_298698_ + 1, 14737632, false);
    }

    protected abstract String toDisplayString(double p_298241_);

    protected abstract int getSampleHeight(double p_298971_);

    protected abstract int getSampleColor(long p_299300_);

    protected int getSampleColor(double p_298217_, double p_298257_, int p_298676_, double p_299233_, int p_298930_, double p_299140_, int p_298542_) {
        p_298217_ = Mth.clamp(p_298217_, p_298257_, p_299140_);
        return p_298217_ < p_299233_
            ? FastColor.ARGB32.lerp((float)((p_298217_ - p_298257_) / (p_299233_ - p_298257_)), p_298676_, p_298930_)
            : FastColor.ARGB32.lerp((float)((p_298217_ - p_299233_) / (p_299140_ - p_299233_)), p_298930_, p_298542_);
    }
}
