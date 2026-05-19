package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Mob Origin Creator — the tabbed shell, mirroring {@code OriginCreatorScreen}
 * (26.1 form: {@code extractRenderState}/{@code GuiGraphicsExtractor}, no
 * {@code flush()}). Implements {@link CreatorHost} so the shared picker/form
 * widgets work verbatim. Save/Apply feedback reuses {@code ClientCreatorState}.
 */
public class MobOriginCreatorScreen extends Screen implements CreatorHost {

    private static final int TITLE_Y = 12, TAB_STRIP_Y = 28, TAB_H = 18;
    private static final int CONTENT_TOP_GAP = 8, HELP_H = 14, BOTTOM_BAR = 34;

    private final Screen parent;
    private final MobOriginDraft draft;
    private final List<MobCreatorTab> tabs = List.of(
        new MobIdentityTab(), new MobPowersTab(), new MobJsonPreviewTab());
    private int activeTab = 0;
    private int panelX, panelW, contentX, contentY, contentW, contentH;

    public MobOriginCreatorScreen(Screen parent, MobOriginDraft draft) {
        super(Component.translatable("screen.neoorigins.mob_creator"));
        this.parent = parent;
        this.draft = draft;
        com.cyberday1.neoorigins.client.ClientCreatorState.clear();
    }

    public MobOriginDraft draft() { return draft; }
    @Override public Font font() { return font; }

    @Override
    public <T extends AbstractWidget> T register(T widget) {
        return addRenderableWidget(widget);
    }

    @Override public void requestRebuild() { rebuild(); }
    @Override public int hostWidth()  { return width; }
    @Override public int hostHeight() { return height; }

    @Override
    protected void init() {
        panelW = Math.min(width - 40, 480);
        panelX = (width - panelW) / 2;
        int stripBottom = TAB_STRIP_Y + TAB_H;
        contentX = panelX;
        contentY = stripBottom + CONTENT_TOP_GAP;
        contentW = panelW;
        contentH = (height - BOTTOM_BAR) - contentY;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int n = tabs.size();
        int tabW = panelW / n;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int tx = panelX + i * tabW;
            int tw = (i == n - 1) ? panelW - i * tabW : tabW;
            addRenderableWidget(Button.builder(tabs.get(i).title(), b -> switchTo(idx))
                .bounds(tx, TAB_STRIP_Y, tw - 2, TAB_H).build());
        }
        MobCreatorTab tab = tabs.get(activeTab);
        tab.init(this, contentX, contentY + HELP_H, contentW, contentH - HELP_H);
        tab.pullFromDraft();

        int by = height - 26, bw = 78, gap = 4;
        int total = bw * 3 + gap * 2;
        int bx = (width - total) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.mob_creator.save"), b -> sendSave())
            .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.mob_creator.apply"), b -> sendApply())
            .bounds(bx + bw + gap, by, bw, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(bx + (bw + gap) * 2, by, bw, 20).build());
    }

    private void sendSave() {
        tabs.get(activeTab).pushToDraft();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.SaveMobOriginPayload(
                com.cyberday1.neoorigins.service.MobOriginDraftJson.toJson(draft)));
    }

    private void sendApply() {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.ApplyMobPackPayload());
    }

    private void switchTo(int idx) {
        if (idx == activeTab) return;
        tabs.get(activeTab).pushToDraft();
        activeTab = idx;
        rebuild();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, CreatorStyle.SCRIM);
        g.centeredText(font, getTitle(), width / 2, TITLE_Y, CreatorStyle.TEXT);
        CreatorStyle.panel(g, contentX - 1, contentY - CONTENT_TOP_GAP / 2 - 1,
            contentW + 2, contentH + CONTENT_TOP_GAP / 2 + 2);
        tabs.get(activeTab).renderBackdrop(g);
        super.extractRenderState(g, mouseX, mouseY, partial);

        int n = tabs.size();
        int tabW = panelW / n;
        int hx = panelX + activeTab * tabW;
        int hw = (activeTab == n - 1) ? panelW - activeTab * tabW : tabW;
        g.fill(hx, TAB_STRIP_Y + TAB_H - 1, hx + hw - 2, TAB_STRIP_Y + TAB_H + 1,
            CreatorStyle.ACCENT);

        MobCreatorTab active = tabs.get(activeTab);
        g.text(font, active.help(), contentX + CreatorStyle.PAD, contentY + 2,
            CreatorStyle.TEXT_DIM, false);
        CreatorStyle.divider(g, contentX + 4, contentY + HELP_H - 3, contentW - 8);
        pendingTip = null;
        active.render(g, mouseX, mouseY, partial,
            contentX, contentY + HELP_H, contentW, contentH - HELP_H);

        String msg = com.cyberday1.neoorigins.client.ClientCreatorState.lastMessage();
        if (!msg.isEmpty()) {
            int color = com.cyberday1.neoorigins.client.ClientCreatorState.lastOk()
                ? CreatorStyle.OK : CreatorStyle.ERR;
            g.centeredText(font, Component.literal(msg), width / 2, height - 42, color);
        }
        if (pendingTip != null && !pendingTip.isEmpty()) {
            CreatorStyle.tooltip(g, font, pendingTip, tipX, tipY, width, height);
        }
    }

    private java.util.List<String> pendingTip;
    private int tipX, tipY;

    @Override
    public void queueTooltip(java.util.List<String> lines, int mx, int my) {
        this.pendingTip = lines; this.tipX = mx; this.tipY = my;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tabs.get(activeTab).mouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override protected void extractBlurredBackground(GuiGraphicsExtractor g) { /* no blur */ }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        tabs.get(activeTab).pushToDraft();
        Minecraft.getInstance().setScreen(parent);
    }
}
