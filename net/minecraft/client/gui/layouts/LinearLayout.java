package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LinearLayout implements Layout {
    private final GridLayout wrapped;
    private final LinearLayout.Orientation orientation;
    private int nextChildIndex = 0;

    private LinearLayout(LinearLayout.Orientation p_265341_) {
        this(0, 0, p_265341_);
    }

    public LinearLayout(int p_265093_, int p_265502_, LinearLayout.Orientation p_265112_) {
        this.wrapped = new GridLayout(p_265093_, p_265502_);
        this.orientation = p_265112_;
    }

    public LinearLayout spacing(int p_294650_) {
        this.orientation.setSpacing(this.wrapped, p_294650_);
        return this;
    }

    public LayoutSettings newCellSettings() {
        return this.wrapped.newCellSettings();
    }

    public LayoutSettings defaultCellSetting() {
        return this.wrapped.defaultCellSetting();
    }

    public <T extends LayoutElement> T addChild(T p_265475_, LayoutSettings p_265684_) {
        return this.orientation.addChild(this.wrapped, p_265475_, this.nextChildIndex++, p_265684_);
    }

    public <T extends LayoutElement> T addChild(T p_265140_) {
        return this.addChild(p_265140_, this.newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T p_294205_, Consumer<LayoutSettings> p_295486_) {
        return this.orientation.addChild(this.wrapped, p_294205_, this.nextChildIndex++, Util.make(this.newCellSettings(), p_295486_));
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> p_265508_) {
        this.wrapped.visitChildren(p_265508_);
    }

    @Override
    public void arrangeElements() {
        this.wrapped.arrangeElements();
    }

    @Override
    public int getWidth() {
        return this.wrapped.getWidth();
    }

    @Override
    public int getHeight() {
        return this.wrapped.getHeight();
    }

    @Override
    public void setX(int p_295684_) {
        this.wrapped.setX(p_295684_);
    }

    @Override
    public void setY(int p_295771_) {
        this.wrapped.setY(p_295771_);
    }

    @Override
    public int getX() {
        return this.wrapped.getX();
    }

    @Override
    public int getY() {
        return this.wrapped.getY();
    }

    public static LinearLayout vertical() {
        return new LinearLayout(LinearLayout.Orientation.VERTICAL);
    }

    public static LinearLayout horizontal() {
        return new LinearLayout(LinearLayout.Orientation.HORIZONTAL);
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Orientation {
        HORIZONTAL,
        VERTICAL;

        void setSpacing(GridLayout p_295925_, int p_295045_) {
            switch (this) {
                case HORIZONTAL:
                    p_295925_.columnSpacing(p_295045_);
                    break;
                case VERTICAL:
                    p_295925_.rowSpacing(p_295045_);
            }
        }

        public <T extends LayoutElement> T addChild(GridLayout p_296325_, T p_294747_, int p_296492_, LayoutSettings p_295163_) {
            return (T)(switch (this) {
                case HORIZONTAL -> (LayoutElement)p_296325_.addChild(p_294747_, 0, p_296492_, p_295163_);
                case VERTICAL -> (LayoutElement)p_296325_.addChild(p_294747_, p_296492_, 0, p_295163_);
            });
        }
    }
}
