package net.minecraft.client.gui.layouts;

import com.mojang.math.Divisor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EqualSpacingLayout extends AbstractLayout {
    private final EqualSpacingLayout.Orientation orientation;
    private final List<EqualSpacingLayout.ChildContainer> children = new ArrayList<>();
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults();

    public EqualSpacingLayout(int p_296391_, int p_295404_, EqualSpacingLayout.Orientation p_296096_) {
        this(0, 0, p_296391_, p_295404_, p_296096_);
    }

    public EqualSpacingLayout(int p_294661_, int p_294554_, int p_295502_, int p_295293_, EqualSpacingLayout.Orientation p_294700_) {
        super(p_294661_, p_294554_, p_295502_, p_295293_);
        this.orientation = p_294700_;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        if (!this.children.isEmpty()) {
            int i = 0;
            int j = this.orientation.getSecondaryLength(this);

            for (EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer : this.children) {
                i += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer);
                j = Math.max(j, this.orientation.getSecondaryLength(equalspacinglayout$childcontainer));
            }

            int k = this.orientation.getPrimaryLength(this) - i;
            int l = this.orientation.getPrimaryPosition(this);
            Iterator<EqualSpacingLayout.ChildContainer> iterator = this.children.iterator();
            EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer1 = iterator.next();
            this.orientation.setPrimaryPosition(equalspacinglayout$childcontainer1, l);
            l += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer1);
            if (this.children.size() >= 2) {
                Divisor divisor = new Divisor(k, this.children.size() - 1);

                while (divisor.hasNext()) {
                    l += divisor.nextInt();
                    EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer2 = iterator.next();
                    this.orientation.setPrimaryPosition(equalspacinglayout$childcontainer2, l);
                    l += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer2);
                }
            }

            int i1 = this.orientation.getSecondaryPosition(this);

            for (EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer3 : this.children) {
                this.orientation.setSecondaryPosition(equalspacinglayout$childcontainer3, i1, j);
            }

            switch (this.orientation) {
                case HORIZONTAL:
                    this.height = j;
                    break;
                case VERTICAL:
                    this.width = j;
            }
        }
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> p_295333_) {
        this.children.forEach(p_296421_ -> p_295333_.accept(p_296421_.child));
    }

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    public <T extends LayoutElement> T addChild(T p_295559_) {
        return this.addChild(p_295559_, this.newChildLayoutSettings());
    }

    public <T extends LayoutElement> T addChild(T p_295964_, LayoutSettings p_296374_) {
        this.children.add(new EqualSpacingLayout.ChildContainer(p_295964_, p_296374_));
        return p_295964_;
    }

    public <T extends LayoutElement> T addChild(T p_295467_, Consumer<LayoutSettings> p_295449_) {
        return this.addChild(p_295467_, Util.make(this.newChildLayoutSettings(), p_295449_));
    }

    @OnlyIn(Dist.CLIENT)
    static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        protected ChildContainer(LayoutElement p_295358_, LayoutSettings p_295638_) {
            super(p_295358_, p_295638_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Orientation {
        HORIZONTAL,
        VERTICAL;

        int getPrimaryLength(LayoutElement p_295781_) {
            return switch (this) {
                case HORIZONTAL -> p_295781_.getWidth();
                case VERTICAL -> p_295781_.getHeight();
            };
        }

        int getPrimaryLength(EqualSpacingLayout.ChildContainer p_295365_) {
            return switch (this) {
                case HORIZONTAL -> p_295365_.getWidth();
                case VERTICAL -> p_295365_.getHeight();
            };
        }

        int getSecondaryLength(LayoutElement p_294949_) {
            return switch (this) {
                case HORIZONTAL -> p_294949_.getHeight();
                case VERTICAL -> p_294949_.getWidth();
            };
        }

        int getSecondaryLength(EqualSpacingLayout.ChildContainer p_295288_) {
            return switch (this) {
                case HORIZONTAL -> p_295288_.getHeight();
                case VERTICAL -> p_295288_.getWidth();
            };
        }

        void setPrimaryPosition(EqualSpacingLayout.ChildContainer p_295434_, int p_294763_) {
            switch (this) {
                case HORIZONTAL:
                    p_295434_.setX(p_294763_, p_295434_.getWidth());
                    break;
                case VERTICAL:
                    p_295434_.setY(p_294763_, p_295434_.getHeight());
            }
        }

        void setSecondaryPosition(EqualSpacingLayout.ChildContainer p_295472_, int p_295477_, int p_296148_) {
            switch (this) {
                case HORIZONTAL:
                    p_295472_.setY(p_295477_, p_296148_);
                    break;
                case VERTICAL:
                    p_295472_.setX(p_295477_, p_296148_);
            }
        }

        int getPrimaryPosition(LayoutElement p_294749_) {
            return switch (this) {
                case HORIZONTAL -> p_294749_.getX();
                case VERTICAL -> p_294749_.getY();
            };
        }

        int getSecondaryPosition(LayoutElement p_295927_) {
            return switch (this) {
                case HORIZONTAL -> p_295927_.getY();
                case VERTICAL -> p_295927_.getX();
            };
        }
    }
}
