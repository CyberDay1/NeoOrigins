package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BandwidthDebugChart extends AbstractDebugChart {
    private static final int MIN_COLOR = -16711681;
    private static final int MID_COLOR = -6250241;
    private static final int MAX_COLOR = -65536;
    private static final int KILOBYTE = 1024;
    private static final int MEGABYTE = 1048576;
    private static final int CHART_TOP_VALUE = 1048576;

    public BandwidthDebugChart(Font p_298747_, SampleStorage p_323505_) {
        super(p_298747_, p_323505_);
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics p_298777_, int p_298875_, int p_298575_, int p_298572_) {
        this.drawLabeledLineAtValue(p_298777_, p_298875_, p_298575_, p_298572_, 64);
        this.drawLabeledLineAtValue(p_298777_, p_298875_, p_298575_, p_298572_, 1024);
        this.drawLabeledLineAtValue(p_298777_, p_298875_, p_298575_, p_298572_, 16384);
        this.drawStringWithShade(p_298777_, toDisplayStringInternal(1048576.0), p_298875_ + 1, p_298572_ - getSampleHeightInternal(1048576.0) + 1);
    }

    private void drawLabeledLineAtValue(GuiGraphics p_298765_, int p_298499_, int p_299090_, int p_299218_, int p_299096_) {
        this.drawLineWithLabel(
            p_298765_, p_298499_, p_299090_, p_299218_ - getSampleHeightInternal((double)p_299096_), toDisplayStringInternal((double)p_299096_)
        );
    }

    private void drawLineWithLabel(GuiGraphics p_298513_, int p_299197_, int p_298663_, int p_299258_, String p_298779_) {
        this.drawStringWithShade(p_298513_, p_298779_, p_299197_ + 1, p_299258_ + 1);
        p_298513_.hLine(RenderType.guiOverlay(), p_299197_, p_299197_ + p_298663_ - 1, p_299258_, -1);
    }

    @Override
    protected String toDisplayString(double p_299213_) {
        return toDisplayStringInternal(toBytesPerSecond(p_299213_));
    }

    private static String toDisplayStringInternal(double p_299224_) {
        if (p_299224_ >= 1048576.0) {
            return String.format(Locale.ROOT, "%.1f MiB/s", p_299224_ / 1048576.0);
        } else {
            return p_299224_ >= 1024.0
                ? String.format(Locale.ROOT, "%.1f KiB/s", p_299224_ / 1024.0)
                : String.format(Locale.ROOT, "%d B/s", Mth.floor(p_299224_));
        }
    }

    @Override
    protected int getSampleHeight(double p_298596_) {
        return getSampleHeightInternal(toBytesPerSecond(p_298596_));
    }

    private static int getSampleHeightInternal(double p_298204_) {
        return (int)Math.round(Math.log(p_298204_ + 1.0) * 60.0 / Math.log(1048576.0));
    }

    @Override
    protected int getSampleColor(long p_298852_) {
        return this.getSampleColor(toBytesPerSecond((double)p_298852_), 0.0, -16711681, 8192.0, -6250241, 1.048576E7, -65536);
    }

    private static double toBytesPerSecond(double p_298720_) {
        return p_298720_ * 20.0;
    }
}
