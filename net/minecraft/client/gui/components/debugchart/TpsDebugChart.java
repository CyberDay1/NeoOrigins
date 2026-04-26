package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleStorage;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TpsDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int TICK_METHOD_COLOR = -6745839;
    private static final int TASK_COLOR = -4548257;
    private static final int OTHER_COLOR = -10547572;
    private final Supplier<Float> msptSupplier;

    public TpsDebugChart(Font p_299254_, SampleStorage p_324399_, Supplier<Float> p_309098_) {
        super(p_299254_, p_324399_);
        this.msptSupplier = p_309098_;
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics p_298653_, int p_298791_, int p_298387_, int p_298869_) {
        float f = (float)TimeUtil.MILLISECONDS_PER_SECOND / this.msptSupplier.get();
        this.drawStringWithShade(p_298653_, String.format("%.1f TPS", f), p_298791_ + 1, p_298869_ - 60 + 1);
    }

    @Override
    protected void drawAdditionalDimensions(GuiGraphics p_321511_, int p_321489_, int p_321791_, int p_321685_) {
        long i = this.sampleStorage.get(p_321685_, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        int j = this.getSampleHeight((double)i);
        p_321511_.fill(RenderType.guiOverlay(), p_321791_, p_321489_ - j, p_321791_ + 1, p_321489_, -6745839);
        long k = this.sampleStorage.get(p_321685_, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
        int l = this.getSampleHeight((double)k);
        p_321511_.fill(RenderType.guiOverlay(), p_321791_, p_321489_ - j - l, p_321791_ + 1, p_321489_ - j, -4548257);
        long i1 = this.sampleStorage.get(p_321685_) - this.sampleStorage.get(p_321685_, TpsDebugDimensions.IDLE.ordinal()) - i - k;
        int j1 = this.getSampleHeight((double)i1);
        p_321511_.fill(RenderType.guiOverlay(), p_321791_, p_321489_ - j1 - l - j, p_321791_ + 1, p_321489_ - l - j, -10547572);
    }

    @Override
    protected long getValueForAggregation(int p_321565_) {
        return this.sampleStorage.get(p_321565_) - this.sampleStorage.get(p_321565_, TpsDebugDimensions.IDLE.ordinal());
    }

    @Override
    protected String toDisplayString(double p_298403_) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(toMilliseconds(p_298403_)));
    }

    @Override
    protected int getSampleHeight(double p_299161_) {
        return (int)Math.round(toMilliseconds(p_299161_) * 60.0 / (double)this.msptSupplier.get().floatValue());
    }

    @Override
    protected int getSampleColor(long p_299243_) {
        float f = this.msptSupplier.get();
        return this.getSampleColor(toMilliseconds((double)p_299243_), (double)f, -16711936, (double)f * 1.125, -256, (double)f * 1.25, -65536);
    }

    private static double toMilliseconds(double p_298223_) {
        return p_298223_ / 1000000.0;
    }
}
