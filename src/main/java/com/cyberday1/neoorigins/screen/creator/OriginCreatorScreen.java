package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Renderable;
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
 * land in Phases 2–4. The backdrop / blur / pause overrides intentionally
 * mirror {@code OriginEditorScreen} (semi-transparent fill would otherwise
 * bleed the blurred world through).
 */
public class OriginCreatorScreen extends Screen implements CreatorHost {

    private static final int TITLE_Y = 12;
    private static final int TAB_STRIP_Y = 28;
    private static final int TAB_H = 18;
    private static final int CONTENT_TOP_GAP = 8;
    private static final int HELP_H = 14;
    private static final int BOTTOM_BAR = 34;

    private final Screen parent;
    private final OriginDraft draft;
    private final List<CreatorTab> tabs = List.of(
        new IdentityTab(), new PowersTab(), new AppearanceTab(),
        new JsonPreviewTab());
    private int activeTab = 0;
    /** Template picker overlay; owns its own input/render while open and
     *  suppresses tab interaction (same pattern as the Identity tab's icon
     *  picker). Null until first opened, then reused so the cached source
     *  list isn't re-fetched per click. */
    private final com.cyberday1.neoorigins.screen.creator.widget.TemplatePickerOverlay
        templatePicker = new com.cyberday1.neoorigins.screen.creator.widget.TemplatePickerOverlay();

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
    public <T extends GuiEventListener & Renderable & NarratableEntry> T register(T widget) {
        return addRenderableWidget(widget);
    }

    /** Input-only registration (no auto-render) — see {@link CreatorHost}. */
    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T registerInputOnly(T widget) {
        return addWidget(widget);
    }

    /**
     * Rebuild the active tab's widget set in place. Tabs whose widget set is
     * dynamic (the Powers tab: power list / type / raw-mode all change which
     * widgets exist) call this after mutating their own state. The active tab
     * must {@code pushToDraft()} itself first — {@link #rebuild} reloads from
     * the draft via {@code pullFromDraft()}.
     */
    public void requestRebuild() { rebuild(); }

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

        // Template picker takes over the screen when open — register only its
        // widgets so clicks/keys land on the overlay, not the tabs underneath.
        if (templatePicker.isOpen()) {
            int pw = Math.min(panelW - 20, 380);
            int ph = Math.min(height - 80, 280);
            int px = (width - pw) / 2;
            int py = (height - ph) / 2;
            templatePicker.build(this, px, py, pw, ph);
            return;
        }

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

        // "Load template" sits in the tab-strip header, right-aligned, so it
        // is reachable from any tab without taking a tab slot. Picker fills
        // the draft fresh — current edits are kept until the user confirms
        // a template (overlay confirm wipes powers + identity in one shot).
        int loadBtnW = 80;
        addRenderableWidget(Button.builder(
                Component.literal("Load template"), b -> openTemplatePicker())
            .bounds(panelX + panelW - loadBtnW, TITLE_Y - 2, loadBtnW, 14).build());

        // Tooltip on/off — sits just left of "Load template". Some users find
        // the hover boxes obstruct the fields underneath, so let them turn the
        // boxes off without losing the rest of the header chrome.
        int tipBtnW = 64, tipGap = 4;
        addRenderableWidget(Button.builder(
                Component.literal(com.cyberday1.neoorigins.client.ClientCreatorState.tooltipsEnabled()
                    ? "Tips: On" : "Tips: Off"),
                b -> { com.cyberday1.neoorigins.client.ClientCreatorState.toggleTooltips(); rebuild(); })
            .bounds(panelX + panelW - loadBtnW - tipGap - tipBtnW, TITLE_Y - 2, tipBtnW, 14).build());

        CreatorTab tab = tabs.get(activeTab);
        tab.init(this, contentX, contentY + HELP_H, contentW, contentH - HELP_H);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.SaveCustomOriginPayload(
                com.cyberday1.neoorigins.service.OriginDraftJson.toJson(draft)));
    }

    private void sendApply() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.ApplyCustomPackPayload());
    }

    private void switchTo(int idx) {
        if (idx == activeTab) return;
        tabs.get(activeTab).pushToDraft();
        activeTab = idx;
        rebuild();
    }

    private void openTemplatePicker() {
        // Commit whatever the active tab has so closing the picker WITHOUT
        // selecting a template leaves prior edits intact.
        tabs.get(activeTab).pushToDraft();
        templatePicker.open(
            t -> {
                com.cyberday1.neoorigins.service.OriginTemplateLoader.load(t, draft);
                // Snap to Identity so the templated id + override hint is
                // the first thing the user sees.
                activeTab = 0;
            },
            this::rebuild);
        rebuild();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, CreatorStyle.SCRIM);
        g.drawCenteredString(font, getTitle(), width / 2, TITLE_Y, CreatorStyle.TEXT);

        if (templatePicker.isOpen()) {
            templatePicker.renderBackdrop(g);
            super.render(g, mouseX, mouseY, partial); // overlay widgets
            templatePicker.render(g);
            return;
        }

        // Content panel frame.
        CreatorStyle.panel(g, contentX - 1, contentY - CONTENT_TOP_GAP / 2 - 1,
            contentW + 2, contentH + CONTENT_TOP_GAP / 2 + 2);

        tabs.get(activeTab).renderBackdrop(g); // behind overlay input widgets
        super.render(g, mouseX, mouseY, partial); // tab-strip + close buttons

        // Active-tab highlight bar under its tab button.
        int n = tabs.size();
        int tabW = panelW / n;
        int hx = panelX + activeTab * tabW;
        int hw = (activeTab == n - 1) ? panelW - activeTab * tabW : tabW;
        g.fill(hx, TAB_STRIP_Y + TAB_H - 1, hx + hw - 2, TAB_STRIP_Y + TAB_H + 1,
            CreatorStyle.ACCENT);

        // Per-tab one-line help + a divider, then the tab body below it.
        CreatorTab active = tabs.get(activeTab);
        g.drawString(font, active.help(), contentX + CreatorStyle.PAD, contentY + 2,
            CreatorStyle.TEXT_DIM, false);
        CreatorStyle.divider(g, contentX + 4, contentY + HELP_H - 3, contentW - 8);
        pendingTip = null; // tabs re-queue it during their render
        active.render(g, mouseX, mouseY, partial,
            contentX, contentY + HELP_H, contentW, contentH - HELP_H);

        // Latest server Save/Apply result, just above the button bar.
        String msg = com.cyberday1.neoorigins.client.ClientCreatorState.lastMessage();
        if (!msg.isEmpty()) {
            int color = com.cyberday1.neoorigins.client.ClientCreatorState.lastOk()
                ? CreatorStyle.OK : CreatorStyle.ERR;
            g.drawCenteredString(font, Component.literal(msg), width / 2, height - 42, color);
        }

        // Hover tooltip drawn dead last so it sits over every widget/box —
        // unless the user has switched tooltips off via the header toggle.
        if (pendingTip != null && !pendingTip.isEmpty()
                && com.cyberday1.neoorigins.client.ClientCreatorState.tooltipsEnabled()) {
            // Lift the tooltip onto its own z-layer (vanilla tooltips use ~400)
            // so the box + text paint cleanly over EditBox/button text instead
            // of blending with it — g.flush() alone left field text bleeding
            // through on some drivers.
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            CreatorStyle.tooltip(g, font, pendingTip, tipX, tipY, width, height);
            g.flush();
            g.pose().popPose();
        }
    }

    private java.util.List<String> pendingTip;
    private int tipX, tipY;

    /** A tab requests its hover tooltip; the screen draws it last (topmost). */
    public void queueTooltip(java.util.List<String> lines, int mx, int my) {
        this.pendingTip = lines;
        this.tipX = mx;
        this.tipY = my;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (templatePicker.isOpen()) {
            if (templatePicker.onScroll(mx, my, sy)) return true;
            return super.mouseScrolled(mx, my, sx, sy);
        }
        if (tabs.get(activeTab).mouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // No-op: render() draws its own backdrop; vanilla's would paint the
        // blurred world behind our semi-transparent fill (mirrors OriginEditorScreen).
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No-op: keep our semi-transparent fill visible.
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // ESC closes only the template picker when it's open — otherwise the
        // user loses the whole creator on a single mis-press while browsing
        // templates. Vanilla's default keyPressed calls onClose() for ESC.
        if (key == 256 /* GLFW_KEY_ESCAPE */ && templatePicker.isOpen()) {
            templatePicker.close();
            rebuild();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (!templatePicker.isOpen()) tabs.get(activeTab).pushToDraft();
        Minecraft.getInstance().setScreen(parent);
    }
}
