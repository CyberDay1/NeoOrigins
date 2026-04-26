package net.minecraft.client.gui.screens.multiplayer;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ServerLinksScreen extends Screen {
    private static final int LINK_BUTTON_WIDTH = 310;
    private static final int DEFAULT_ITEM_HEIGHT = 25;
    private static final Component TITLE = Component.translatable("menu.server_links.title");
    private final Screen lastScreen;
    @Nullable
    private ServerLinksScreen.LinkList list;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    final ServerLinks links;

    public ServerLinksScreen(Screen p_350433_, ServerLinks p_350491_) {
        super(TITLE);
        this.lastScreen = p_350433_;
        this.links = p_350491_;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.list = this.layout.addToContents(new ServerLinksScreen.LinkList(this.minecraft, this.width, this));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, p_350487_ -> this.onClose()).width(200).build());
        this.layout.visitWidgets(p_350620_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_350620_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @OnlyIn(Dist.CLIENT)
    static class LinkList extends ContainerObjectSelectionList<ServerLinksScreen.LinkListEntry> {
        public LinkList(Minecraft p_350783_, int p_350730_, ServerLinksScreen p_350695_) {
            super(p_350783_, p_350730_, p_350695_.layout.getContentHeight(), p_350695_.layout.getHeaderHeight(), 25);
            p_350695_.links.entries().forEach(p_350872_ -> this.addEntry(new ServerLinksScreen.LinkListEntry(p_350695_, p_350872_)));
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        @Override
        public void updateSize(int p_350967_, HeaderAndFooterLayout p_350748_) {
            super.updateSize(p_350967_, p_350748_);
            int i = p_350967_ / 2 - 155;
            this.children().forEach(p_350545_ -> p_350545_.button.setX(i));
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class LinkListEntry extends ContainerObjectSelectionList.Entry<ServerLinksScreen.LinkListEntry> {
        final AbstractWidget button;

        LinkListEntry(Screen p_350500_, ServerLinks.Entry p_350677_) {
            this.button = Button.builder(p_350677_.displayName(), ConfirmLinkScreen.confirmLink(p_350500_, p_350677_.link(), false)).width(310).build();
        }

        @Override
        public void render(
            GuiGraphics p_350577_,
            int p_350511_,
            int p_350936_,
            int p_350596_,
            int p_350517_,
            int p_351059_,
            int p_350806_,
            int p_351038_,
            boolean p_350365_,
            float p_350788_
        ) {
            this.button.setY(p_350936_);
            this.button.render(p_350577_, p_350806_, p_351038_, p_350788_);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }
}
