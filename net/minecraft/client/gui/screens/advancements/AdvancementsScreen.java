package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AdvancementsScreen extends Screen implements ClientAdvancements.Listener {
    private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    public static final int WINDOW_WIDTH = 252;
    public static final int WINDOW_HEIGHT = 140;
    private static final int WINDOW_INSIDE_X = 9;
    private static final int WINDOW_INSIDE_Y = 18;
    public static final int WINDOW_INSIDE_WIDTH = 234;
    public static final int WINDOW_INSIDE_HEIGHT = 113;
    private static final int WINDOW_TITLE_X = 8;
    private static final int WINDOW_TITLE_Y = 6;
    public static final int BACKGROUND_TILE_WIDTH = 16;
    public static final int BACKGROUND_TILE_HEIGHT = 16;
    public static final int BACKGROUND_TILE_COUNT_X = 14;
    public static final int BACKGROUND_TILE_COUNT_Y = 7;
    private static final double SCROLL_SPEED = 16.0;
    private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
    private static final Component TITLE = Component.translatable("gui.advancements");
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private final Screen lastScreen;
    private final ClientAdvancements advancements;
    private final Map<AdvancementHolder, AdvancementTab> tabs = Maps.newLinkedHashMap();
    @Nullable
    private AdvancementTab selectedTab;
    private boolean isScrolling;
    private static int tabPage, maxPages;

    public AdvancementsScreen(ClientAdvancements p_97340_) {
        this(p_97340_, null);
    }

    public AdvancementsScreen(ClientAdvancements p_331393_, @Nullable Screen p_331693_) {
        super(TITLE);
        this.advancements = p_331393_;
        this.lastScreen = p_331693_;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.tabs.clear();
        this.selectedTab = null;
        this.advancements.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            AdvancementTab advancementtab = this.tabs.values().iterator().next();
            this.advancements.setSelectedTab(advancementtab.getRootNode().holder(), true);
        } else {
            this.advancements.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getRootNode().holder(), true);
        }

        if (this.tabs.size() > AdvancementTabType.MAX_TABS) {
            int guiLeft = (this.width - 252) / 2;
            int guiTop = (this.height - 140) / 2;
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("<"), b -> tabPage = Math.max(tabPage - 1, 0         ))
                    .pos(guiLeft, guiTop - 50).size(20, 20).build());
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(">"), b -> tabPage = Math.min(tabPage + 1, maxPages))
                    .pos(guiLeft + WINDOW_WIDTH - 20, guiTop - 50).size(20, 20).build());
            maxPages = this.tabs.size() / AdvancementTabType.MAX_TABS;
        }

        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, p_331557_ -> this.onClose()).width(200).build());
        this.layout.visitWidgets(p_332019_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_332019_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public void removed() {
        this.advancements.setListener(null);
        ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
        if (clientpacketlistener != null) {
            clientpacketlistener.send(ServerboundSeenAdvancementsPacket.closedScreen());
        }
    }

    @Override
    public boolean mouseClicked(double p_97343_, double p_97344_, int p_97345_) {
        if (p_97345_ == 0) {
            int i = (this.width - 252) / 2;
            int j = (this.height - 140) / 2;

            for (AdvancementTab advancementtab : this.tabs.values()) {
                if (advancementtab.getPage() == tabPage && advancementtab.isMouseOver(i, j, p_97343_, p_97344_)) {
                    this.advancements.setSelectedTab(advancementtab.getRootNode().holder(), true);
                    break;
                }
            }
        }

        return super.mouseClicked(p_97343_, p_97344_, p_97345_);
    }

    @Override
    public boolean keyPressed(int p_97353_, int p_97354_, int p_97355_) {
        if (this.minecraft.options.keyAdvancements.matches(p_97353_, p_97354_)) {
            this.minecraft.setScreen(null);
            this.minecraft.mouseHandler.grabMouse();
            return true;
        } else {
            return super.keyPressed(p_97353_, p_97354_, p_97355_);
        }
    }

    @Override
    public void render(GuiGraphics p_282589_, int p_282255_, int p_283354_, float p_283123_) {
        super.render(p_282589_, p_282255_, p_283354_, p_283123_);
        int i = (this.width - 252) / 2;
        int j = (this.height - 140) / 2;
        if (maxPages != 0) {
            net.minecraft.network.chat.Component page = Component.literal(String.format("%d / %d", tabPage + 1, maxPages + 1));
            int width = this.font.width(page);
            p_282589_.drawString(this.font, page.getVisualOrderText(), i + (252 / 2) - (width / 2), j - 44, -1);
        }
        this.renderInside(p_282589_, p_282255_, p_283354_, i, j);
        this.renderWindow(p_282589_, i, j);
        this.renderTooltips(p_282589_, p_282255_, p_283354_, i, j);
    }

    @Override
    public boolean mouseDragged(double p_97347_, double p_97348_, int p_97349_, double p_97350_, double p_97351_) {
        if (p_97349_ != 0) {
            this.isScrolling = false;
            return false;
        } else {
            if (!this.isScrolling) {
                this.isScrolling = true;
            } else if (this.selectedTab != null) {
                this.selectedTab.scroll(p_97350_, p_97351_);
            }

            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double p_295690_, double p_295286_, double p_295339_, double p_296270_) {
        if (this.selectedTab != null) {
            this.selectedTab.scroll(p_295339_ * 16.0, p_296270_ * 16.0);
            return true;
        } else {
            return false;
        }
    }

    private void renderInside(GuiGraphics p_282012_, int p_97375_, int p_97376_, int p_97377_, int p_97378_) {
        AdvancementTab advancementtab = this.selectedTab;
        if (advancementtab == null) {
            p_282012_.fill(p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113, -16777216);
            int i = p_97377_ + 9 + 117;
            p_282012_.drawCenteredString(this.font, NO_ADVANCEMENTS_LABEL, i, p_97378_ + 18 + 56 - 9 / 2, -1);
            p_282012_.drawCenteredString(this.font, VERY_SAD_LABEL, i, p_97378_ + 18 + 113 - 9, -1);
        } else {
            advancementtab.drawContents(p_282012_, p_97377_ + 9, p_97378_ + 18);
        }
    }

    public void renderWindow(GuiGraphics p_283395_, int p_281890_, int p_282532_) {
        RenderSystem.enableBlend();
        p_283395_.blit(WINDOW_LOCATION, p_281890_, p_282532_, 0, 0, 252, 140);
        if (this.tabs.size() > 1) {
            for (AdvancementTab advancementtab : this.tabs.values()) {
                if (advancementtab.getPage() == tabPage)
                advancementtab.drawTab(p_283395_, p_281890_, p_282532_, advancementtab == this.selectedTab);
            }

            for (AdvancementTab advancementtab1 : this.tabs.values()) {
                if (advancementtab1.getPage() == tabPage)
                advancementtab1.drawIcon(p_283395_, p_281890_, p_282532_);
            }
        }

        p_283395_.drawString(this.font, this.selectedTab != null ? this.selectedTab.getTitle() : TITLE, p_281890_ + 8, p_282532_ + 6, 4210752, false);
    }

    private void renderTooltips(GuiGraphics p_282784_, int p_283556_, int p_282458_, int p_281519_, int p_283371_) {
        if (this.selectedTab != null) {
            p_282784_.pose().pushPose();
            p_282784_.pose().translate((float)(p_281519_ + 9), (float)(p_283371_ + 18), 400.0F);
            RenderSystem.enableDepthTest();
            this.selectedTab.drawTooltips(p_282784_, p_283556_ - p_281519_ - 9, p_282458_ - p_283371_ - 18, p_281519_, p_283371_);
            RenderSystem.disableDepthTest();
            p_282784_.pose().popPose();
        }

        if (this.tabs.size() > 1) {
            for (AdvancementTab advancementtab : this.tabs.values()) {
                if (advancementtab.getPage() == tabPage && advancementtab.isMouseOver(p_281519_, p_283371_, (double)p_283556_, (double)p_282458_)) {
                    p_282784_.renderTooltip(this.font, advancementtab.getTitle(), p_283556_, p_282458_);
                }
            }
        }
    }

    @Override
    public void onAddAdvancementRoot(AdvancementNode p_301276_) {
        AdvancementTab advancementtab = AdvancementTab.create(this.minecraft, this, this.tabs.size(), p_301276_);
        if (advancementtab != null) {
            this.tabs.put(p_301276_.holder(), advancementtab);
        }
    }

    @Override
    public void onRemoveAdvancementRoot(AdvancementNode p_301028_) {
    }

    @Override
    public void onAddAdvancementTask(AdvancementNode p_301205_) {
        AdvancementTab advancementtab = this.getTab(p_301205_);
        if (advancementtab != null) {
            advancementtab.addAdvancement(p_301205_);
        }
    }

    @Override
    public void onRemoveAdvancementTask(AdvancementNode p_301004_) {
    }

    @Override
    public void onUpdateAdvancementProgress(AdvancementNode p_301161_, AdvancementProgress p_97369_) {
        AdvancementWidget advancementwidget = this.getAdvancementWidget(p_301161_);
        if (advancementwidget != null) {
            advancementwidget.setProgress(p_97369_);
        }
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder p_301084_) {
        this.selectedTab = this.tabs.get(p_301084_);
    }

    @Override
    public void onAdvancementsCleared() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public AdvancementWidget getAdvancementWidget(AdvancementNode p_300894_) {
        AdvancementTab advancementtab = this.getTab(p_300894_);
        return advancementtab == null ? null : advancementtab.getWidget(p_300894_.holder());
    }

    @Nullable
    private AdvancementTab getTab(AdvancementNode p_301151_) {
        AdvancementNode advancementnode = p_301151_.root();
        return this.tabs.get(advancementnode.holder());
    }
}
