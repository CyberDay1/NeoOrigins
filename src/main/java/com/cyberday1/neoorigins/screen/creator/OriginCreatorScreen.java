package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 2.1 in-game origin/class creator — the tabbed shell.
 *
 * <p>Owns the tab strip, the shared {@link OriginDraft}, widget lifecycle, and
 * the backdrop; each {@link CreatorTab} fills one page. Replaces routing to the
 * old runtime-only {@code OriginEditorScreen} (kept in-tree but no longer
 * opened — see {@code ClientOriginState#openEditorScreen}).
 *
 * <p>Phase 1 scope: the framework — tab switching, draft plumbing, layout.
 * Tabs are stubs; persistence, the server-auth gate, and real tab content
 * land in Phases 2–4. The blur / pause overrides intentionally mirror the
 * 26.1 {@code OriginEditorScreen} (semi-transparent fill would otherwise
 * bleed the blurred world through).
 */
public class OriginCreatorScreen extends Screen {

    private static final int TITLE_Y = 12;
    private static final int TAB_STRIP_Y = 28;
    private static final int TAB_H = 18;
    private static final int CONTENT_TOP_GAP = 8;
    private static final int BOTTOM_BAR = 34;

    private final Screen parent;
    private final OriginDraft draft;
    private final List<CreatorTab> tabs = List.of(
        new IdentityTab(), new PowersTab(), new AppearanceTab(),
        new LayerTab(), new JsonPreviewTab());
    private int activeTab = 0;

    private int panelX, panelW;
    private int contentX, contentY, contentW, contentH;

    public OriginCreatorScreen(Screen parent, OriginDraft draft) {
        super(Component.translatable("screen.neoorigins.creator"));
        this.parent = parent;
        this.draft = draft;
        com.cyberday1.neoorigins.client.ClientCreatorState.clear();
    }

    /** The shared model every tab reads/writes. */
    public OriginDraft draft() { return draft; }

    /** Font accessor for tabs (Screen#font is protected). */
    public Font font() { return font; }

    /** Let tabs register widgets through the screen's protected machinery. */
    public <T extends AbstractWidget> T register(T widget) {
        return addRenderableWidget(widget);
    }

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

        // Tab strip — one button per tab, evenly split across the panel.
        int n = tabs.size();
        int tabW = panelW / n;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int tx = panelX + i * tabW;
            int tw = (i == n - 1) ? panelW - i * tabW : tabW; // absorb rounding
            addRenderableWidget(Button.builder(tabs.get(i).title(), b -> switchTo(idx))
                .bounds(tx, TAB_STRIP_Y, tw - 2, TAB_H).build());
        }

        CreatorTab tab = tabs.get(activeTab);
        tab.init(this, contentX, contentY, contentW, contentH);
        tab.pullFromDraft();

        // Save | Apply | Close — server gates each; result shown above the bar.
        int by = height - 26, bw = 78, gap = 4;
        int total = bw * 3 + gap * 2;
        int bx = (width - total) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.creator.save"), b -> sendSave())
            .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.creator.apply"), b -> sendApply())
            .bounds(bx + bw + gap, by, bw, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(bx + (bw + gap) * 2, by, bw, 20).build());
    }

    private void sendSave() {
        tabs.get(activeTab).pushToDraft();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.SaveCustomOriginPayload(
                com.cyberday1.neoorigins.service.OriginDraftJson.toJson(draft)));
    }

    private void sendApply() {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.ApplyCustomPackPayload());
    }

    private void switchTo(int idx) {
        if (idx == activeTab) return;
        tabs.get(activeTab).pushToDraft();
        activeTab = idx;
        rebuild();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);
        g.centeredText(font, getTitle(), width / 2, TITLE_Y, 0xFFFFFFFF);

        // Content panel frame.
        g.fill(contentX, contentY - CONTENT_TOP_GAP / 2,
            contentX + contentW, contentY + contentH, 0xFF09091A);
        g.outline(contentX - 1, contentY - CONTENT_TOP_GAP / 2 - 1,
            contentW + 2, contentH + CONTENT_TOP_GAP / 2 + 2, 0xFF252540);

        super.extractRenderState(g, mouseX, mouseY, partial); // tab-strip + close

        // Active-tab highlight bar under its tab button.
        int n = tabs.size();
        int tabW = panelW / n;
        int hx = panelX + activeTab * tabW;
        int hw = (activeTab == n - 1) ? panelW - activeTab * tabW : tabW;
        g.fill(hx, TAB_STRIP_Y + TAB_H - 1, hx + hw - 2, TAB_STRIP_Y + TAB_H + 1, 0xFF4A90D9);

        tabs.get(activeTab).render(g, mouseX, mouseY, partial,
            contentX, contentY, contentW, contentH);

        // Latest server Save/Apply result, just above the button bar.
        String msg = com.cyberday1.neoorigins.client.ClientCreatorState.lastMessage();
        if (!msg.isEmpty()) {
            int color = com.cyberday1.neoorigins.client.ClientCreatorState.lastOk()
                ? 0xFF55DD77 : 0xFFDD5555;
            g.centeredText(font, Component.literal(msg), width / 2, height - 42, color);
        }
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
