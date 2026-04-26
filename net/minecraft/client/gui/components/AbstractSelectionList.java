package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {
    protected static final int SCROLLBAR_WIDTH = 6;
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    private static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    protected final Minecraft minecraft;
    protected final int itemHeight;
    private final List<E> children = new AbstractSelectionList.TrackedList();
    protected boolean centerListVertically = true;
    private double scrollAmount;
    private boolean renderHeader;
    protected int headerHeight;
    private boolean scrolling;
    @Nullable
    private E selected;
    @Nullable
    private E hovered;

    public AbstractSelectionList(Minecraft p_93404_, int p_93405_, int p_93406_, int p_93407_, int p_93408_) {
        super(0, p_93407_, p_93405_, p_93406_, CommonComponents.EMPTY);
        this.minecraft = p_93404_;
        this.itemHeight = p_93408_;
    }

    protected void setRenderHeader(boolean p_93474_, int p_93475_) {
        this.renderHeader = p_93474_;
        this.headerHeight = p_93475_;
        if (!p_93474_) {
            this.headerHeight = 0;
        }
    }

    public int getRowWidth() {
        return 220;
    }

    @Nullable
    public E getSelected() {
        return this.selected;
    }

    public void setSelected(@Nullable E p_93462_) {
        this.selected = p_93462_;
    }

    public E getFirstElement() {
        return this.children.get(0);
    }

    @Nullable
    public E getFocused() {
        return (E)super.getFocused();
    }

    @Override
    public final List<E> children() {
        return this.children;
    }

    protected void clearEntries() {
        this.children.clear();
        this.selected = null;
    }

    protected void replaceEntries(Collection<E> p_93470_) {
        this.clearEntries();
        this.children.addAll(p_93470_);
    }

    protected E getEntry(int p_93501_) {
        return this.children().get(p_93501_);
    }

    protected int addEntry(E p_93487_) {
        this.children.add(p_93487_);
        return this.children.size() - 1;
    }

    protected void addEntryToTop(E p_239858_) {
        double d0 = (double)this.getMaxScroll() - this.getScrollAmount();
        this.children.add(0, p_239858_);
        this.setScrollAmount((double)this.getMaxScroll() - d0);
    }

    protected boolean removeEntryFromTop(E p_239046_) {
        double d0 = (double)this.getMaxScroll() - this.getScrollAmount();
        boolean flag = this.removeEntry(p_239046_);
        this.setScrollAmount((double)this.getMaxScroll() - d0);
        return flag;
    }

    protected int getItemCount() {
        return this.children().size();
    }

    protected boolean isSelectedItem(int p_93504_) {
        return Objects.equals(this.getSelected(), this.children().get(p_93504_));
    }

    @Nullable
    protected final E getEntryAtPosition(double p_93413_, double p_93414_) {
        int i = this.getRowWidth() / 2;
        int j = this.getX() + this.width / 2;
        int k = j - i;
        int l = j + i;
        int i1 = Mth.floor(p_93414_ - (double)this.getY()) - this.headerHeight + (int)this.getScrollAmount() - 4;
        int j1 = i1 / this.itemHeight;
        return p_93413_ >= (double)k && p_93413_ <= (double)l && j1 >= 0 && i1 >= 0 && j1 < this.getItemCount() ? this.children().get(j1) : null;
    }

    public void updateSize(int p_331980_, HeaderAndFooterLayout p_331821_) {
        this.updateSizeAndPosition(p_331980_, p_331821_.getContentHeight(), p_331821_.getHeaderHeight());
    }

    public void updateSizeAndPosition(int p_332028_, int p_330750_, int p_331681_) {
        this.setSize(p_332028_, p_330750_);
        this.setPosition(0, p_331681_);
        this.clampScrollAmount();
    }

    protected int getMaxPosition() {
        return this.getItemCount() * this.itemHeight + this.headerHeight;
    }

    protected boolean clickedHeader(int p_93431_, int p_93432_) {
        return false;
    }

    protected void renderHeader(GuiGraphics p_282337_, int p_93444_, int p_93445_) {
    }

    protected void renderDecorations(GuiGraphics p_281477_, int p_93459_, int p_93460_) {
    }

    @Override
    public void renderWidget(GuiGraphics p_282708_, int p_283242_, int p_282891_, float p_283683_) {
        this.hovered = this.isMouseOver((double)p_283242_, (double)p_282891_) ? this.getEntryAtPosition((double)p_283242_, (double)p_282891_) : null;
        this.renderListBackground(p_282708_);
        this.enableScissor(p_282708_);
        if (this.renderHeader) {
            int i = this.getRowLeft();
            int j = this.getY() + 4 - (int)this.getScrollAmount();
            this.renderHeader(p_282708_, i, j);
        }

        this.renderListItems(p_282708_, p_283242_, p_282891_, p_283683_);
        p_282708_.disableScissor();
        this.renderListSeparators(p_282708_);
        if (this.scrollbarVisible()) {
            int l = this.getScrollbarPosition();
            int i1 = (int)((float)(this.height * this.height) / (float)this.getMaxPosition());
            i1 = Mth.clamp(i1, 32, this.height - 8);
            int k = (int)this.getScrollAmount() * (this.height - i1) / this.getMaxScroll() + this.getY();
            if (k < this.getY()) {
                k = this.getY();
            }

            RenderSystem.enableBlend();
            p_282708_.blitSprite(SCROLLER_BACKGROUND_SPRITE, l, this.getY(), 6, this.getHeight());
            p_282708_.blitSprite(SCROLLER_SPRITE, l, k, 6, i1);
            RenderSystem.disableBlend();
        }

        this.renderDecorations(p_282708_, p_283242_, p_282891_);
        RenderSystem.disableBlend();
    }

    protected boolean scrollbarVisible() {
        return this.getMaxScroll() > 0;
    }

    protected void renderListSeparators(GuiGraphics p_331248_) {
        RenderSystem.enableBlend();
        ResourceLocation resourcelocation = this.minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
        ResourceLocation resourcelocation1 = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        p_331248_.blit(resourcelocation, this.getX(), this.getY() - 2, 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
        p_331248_.blit(resourcelocation1, this.getX(), this.getBottom(), 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
        RenderSystem.disableBlend();
    }

    protected void renderListBackground(GuiGraphics p_331297_) {
        RenderSystem.enableBlend();
        ResourceLocation resourcelocation = this.minecraft.level == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND;
        p_331297_.blit(
            resourcelocation,
            this.getX(),
            this.getY(),
            (float)this.getRight(),
            (float)(this.getBottom() + (int)this.getScrollAmount()),
            this.getWidth(),
            this.getHeight(),
            32,
            32
        );
        RenderSystem.disableBlend();
    }

    protected void enableScissor(GuiGraphics p_282811_) {
        p_282811_.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
    }

    protected void centerScrollOn(E p_93495_) {
        this.setScrollAmount((double)(this.children().indexOf(p_93495_) * this.itemHeight + this.itemHeight / 2 - this.height / 2));
    }

    protected void ensureVisible(E p_93499_) {
        int i = this.getRowTop(this.children().indexOf(p_93499_));
        int j = i - this.getY() - 4 - this.itemHeight;
        if (j < 0) {
            this.scroll(j);
        }

        int k = this.getBottom() - i - this.itemHeight - this.itemHeight;
        if (k < 0) {
            this.scroll(-k);
        }
    }

    private void scroll(int p_93430_) {
        this.setScrollAmount(this.getScrollAmount() + (double)p_93430_);
    }

    public double getScrollAmount() {
        return this.scrollAmount;
    }

    public void setClampedScrollAmount(double p_345538_) {
        this.scrollAmount = Mth.clamp(p_345538_, 0.0, (double)this.getMaxScroll());
    }

    public void setScrollAmount(double p_93411_) {
        this.setClampedScrollAmount(p_93411_);
    }

    public void clampScrollAmount() {
        this.setClampedScrollAmount(this.getScrollAmount());
    }

    public int getMaxScroll() {
        return Math.max(0, this.getMaxPosition() - (this.height - 4));
    }

    protected void updateScrollingState(double p_93482_, double p_93483_, int p_93484_) {
        this.scrolling = p_93484_ == 0 && p_93482_ >= (double)this.getScrollbarPosition() && p_93482_ < (double)(this.getScrollbarPosition() + 6);
    }

    protected int getScrollbarPosition() {
        return this.getDefaultScrollbarPosition();
    }

    protected int getDefaultScrollbarPosition() {
        return this.getRealRowRight() + this.getListOutlinePadding();
    }

    private int getListOutlinePadding() {
        return 10;
    }

    protected boolean isValidMouseClick(int p_301202_) {
        return p_301202_ == 0;
    }

    @Override
    public boolean mouseClicked(double p_93420_, double p_93421_, int p_93422_) {
        if (!this.isValidMouseClick(p_93422_)) {
            return false;
        } else {
            this.updateScrollingState(p_93420_, p_93421_, p_93422_);
            if (!this.isMouseOver(p_93420_, p_93421_)) {
                return false;
            } else {
                E e = this.getEntryAtPosition(p_93420_, p_93421_);
                if (e != null) {
                    if (e.mouseClicked(p_93420_, p_93421_, p_93422_)) {
                        E e1 = this.getFocused();
                        if (e1 != e && e1 instanceof ContainerEventHandler containereventhandler) {
                            containereventhandler.setFocused(null);
                        }

                        this.setFocused(e);
                        this.setDragging(true);
                        return true;
                    }
                } else if (this.clickedHeader(
                    (int)(p_93420_ - (double)(this.getX() + this.width / 2 - this.getRowWidth() / 2)),
                    (int)(p_93421_ - (double)this.getY()) + (int)this.getScrollAmount() - 4
                )) {
                    return true;
                }

                return this.scrolling;
            }
        }
    }

    @Override
    public boolean mouseReleased(double p_93491_, double p_93492_, int p_93493_) {
        return this.getFocused() != null ? this.getFocused().mouseReleased(p_93491_, p_93492_, p_93493_) : false;
    }

    @Override
    public boolean mouseDragged(double p_93424_, double p_93425_, int p_93426_, double p_93427_, double p_93428_) {
        if (super.mouseDragged(p_93424_, p_93425_, p_93426_, p_93427_, p_93428_)) {
            return true;
        } else if (p_93426_ == 0 && this.scrolling) {
            if (p_93425_ < (double)this.getY()) {
                this.setScrollAmount(0.0);
            } else if (p_93425_ > (double)this.getBottom()) {
                this.setScrollAmount((double)this.getMaxScroll());
            } else {
                double d0 = (double)Math.max(1, this.getMaxScroll());
                int i = this.height;
                int j = Mth.clamp((int)((float)(i * i) / (float)this.getMaxPosition()), 32, i - 8);
                double d1 = Math.max(1.0, d0 / (double)(i - j));
                this.setScrollAmount(this.getScrollAmount() + p_93428_ * d1);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double p_93416_, double p_93417_, double p_93418_, double p_294369_) {
        this.setScrollAmount(this.getScrollAmount() - p_294369_ * (double)this.itemHeight / 2.0);
        return true;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener p_265738_) {
        super.setFocused(p_265738_);
        int i = this.children.indexOf(p_265738_);
        if (i >= 0) {
            E e = this.children.get(i);
            this.setSelected(e);
            if (this.minecraft.getLastInputType().isKeyboard()) {
                this.ensureVisible(e);
            }
        }
    }

    @Nullable
    protected E nextEntry(ScreenDirection p_265160_) {
        return this.nextEntry(p_265160_, p_93510_ -> true);
    }

    @Nullable
    protected E nextEntry(ScreenDirection p_265210_, Predicate<E> p_265604_) {
        return this.nextEntry(p_265210_, p_265604_, this.getSelected());
    }

    @Nullable
    protected E nextEntry(ScreenDirection p_265159_, Predicate<E> p_265109_, @Nullable E p_265379_) {
        int i = switch (p_265159_) {
            case RIGHT, LEFT -> 0;
            case UP -> -1;
            case DOWN -> 1;
        };
        if (!this.children().isEmpty() && i != 0) {
            int j;
            if (p_265379_ == null) {
                j = i > 0 ? 0 : this.children().size() - 1;
            } else {
                j = this.children().indexOf(p_265379_) + i;
            }

            for (int k = j; k >= 0 && k < this.children.size(); k += i) {
                E e = this.children().get(k);
                if (p_265109_.test(e)) {
                    return e;
                }
            }
        }

        return null;
    }

    @Override
    public boolean isMouseOver(double p_93479_, double p_93480_) {
        return p_93480_ >= (double)this.getY()
            && p_93480_ <= (double)this.getBottom()
            && p_93479_ >= (double)this.getX()
            && p_93479_ <= (double)this.getRight();
    }

    protected void renderListItems(GuiGraphics p_282079_, int p_239229_, int p_239230_, float p_239231_) {
        int i = this.getRowLeft();
        int j = this.getRowWidth();
        int k = this.itemHeight - 4;
        int l = this.getItemCount();

        for (int i1 = 0; i1 < l; i1++) {
            int j1 = this.getRowTop(i1);
            int k1 = this.getRowBottom(i1);
            if (k1 >= this.getY() && j1 <= this.getBottom()) {
                this.renderItem(p_282079_, p_239229_, p_239230_, p_239231_, i1, i, j1, j, k);
            }
        }
    }

    protected void renderItem(
        GuiGraphics p_282205_, int p_238966_, int p_238967_, float p_238968_, int p_238969_, int p_238970_, int p_238971_, int p_238972_, int p_238973_
    ) {
        E e = this.getEntry(p_238969_);
        e.renderBack(p_282205_, p_238969_, p_238971_, p_238970_, p_238972_, p_238973_, p_238966_, p_238967_, Objects.equals(this.hovered, e), p_238968_);
        if (this.isSelectedItem(p_238969_)) {
            int i = this.isFocused() ? -1 : -8355712;
            this.renderSelection(p_282205_, p_238971_, p_238972_, p_238973_, i, -16777216);
        }

        e.render(p_282205_, p_238969_, p_238971_, p_238970_, p_238972_, p_238973_, p_238966_, p_238967_, Objects.equals(this.hovered, e), p_238968_);
    }

    protected void renderSelection(GuiGraphics p_283589_, int p_240142_, int p_240143_, int p_240144_, int p_240145_, int p_240146_) {
        int i = this.getX() + (this.width - p_240143_) / 2;
        int j = this.getX() + (this.width + p_240143_) / 2;
        p_283589_.fill(i, p_240142_ - 2, j, p_240142_ + p_240144_ + 2, p_240145_);
        p_283589_.fill(i + 1, p_240142_ - 1, j - 1, p_240142_ + p_240144_ + 1, p_240146_);
    }

    public int getRowLeft() {
        return this.getX() + this.width / 2 - this.getRowWidth() / 2 + 2;
    }

    private int getRealRowLeft() {
        return this.getX() + this.width / 2 - this.getRowWidth() / 2;
    }

    public int getRowRight() {
        return this.getRowLeft() + this.getRowWidth();
    }

    private int getRealRowRight() {
        return this.getRealRowLeft() + this.getRowWidth();
    }

    protected int getRowTop(int p_93512_) {
        return this.getY() + 4 - (int)this.getScrollAmount() + p_93512_ * this.itemHeight + this.headerHeight;
    }

    protected int getRowBottom(int p_93486_) {
        return this.getRowTop(p_93486_) + this.itemHeight;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        if (this.isFocused()) {
            return NarratableEntry.NarrationPriority.FOCUSED;
        } else {
            return this.hovered != null ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
        }
    }

    @Nullable
    protected E remove(int p_93515_) {
        E e = this.children.get(p_93515_);
        return this.removeEntry(this.children.get(p_93515_)) ? e : null;
    }

    protected boolean removeEntry(E p_93503_) {
        boolean flag = this.children.remove(p_93503_);
        if (flag && p_93503_ == this.getSelected()) {
            this.setSelected(null);
        }

        return flag;
    }

    @Nullable
    protected E getHovered() {
        return this.hovered;
    }

    void bindEntryToSelf(AbstractSelectionList.Entry<E> p_93506_) {
        p_93506_.list = this;
    }

    protected void narrateListElementPosition(NarrationElementOutput p_168791_, E p_168792_) {
        List<E> list = this.children();
        if (list.size() > 1) {
            int i = list.indexOf(p_168792_);
            if (i != -1) {
                p_168791_.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list", i + 1, list.size()));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected abstract static class Entry<E extends AbstractSelectionList.Entry<E>> implements GuiEventListener {
        @Deprecated
        protected AbstractSelectionList<E> list;

        @Override
        public void setFocused(boolean p_265302_) {
        }

        @Override
        public boolean isFocused() {
            return this.list.getFocused() == this;
        }

        public abstract void render(
            GuiGraphics p_283112_,
            int p_93524_,
            int p_93525_,
            int p_93526_,
            int p_93527_,
            int p_93528_,
            int p_93529_,
            int p_93530_,
            boolean p_93531_,
            float p_93532_
        );

        public void renderBack(
            GuiGraphics p_282673_,
            int p_275556_,
            int p_275667_,
            int p_275713_,
            int p_275408_,
            int p_275330_,
            int p_275603_,
            int p_275450_,
            boolean p_275434_,
            float p_275384_
        ) {
        }

        @Override
        public boolean isMouseOver(double p_93537_, double p_93538_) {
            return Objects.equals(this.list.getEntryAtPosition(p_93537_, p_93538_), this);
        }
    }

    @OnlyIn(Dist.CLIENT)
    class TrackedList extends AbstractList<E> {
        private final List<E> delegate = Lists.newArrayList();

        public E get(int p_93557_) {
            return this.delegate.get(p_93557_);
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        public E set(int p_93559_, E p_93560_) {
            E e = this.delegate.set(p_93559_, p_93560_);
            AbstractSelectionList.this.bindEntryToSelf(p_93560_);
            return e;
        }

        public void add(int p_93567_, E p_93568_) {
            this.delegate.add(p_93567_, p_93568_);
            AbstractSelectionList.this.bindEntryToSelf(p_93568_);
        }

        public E remove(int p_93565_) {
            return this.delegate.remove(p_93565_);
        }
    }
}
