package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class OriginSelectionScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_TOP        = 44;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int SEARCH_H         = 16;
    private static final int SEARCH_GAP       = 3;
    private static final int LIST_BTN_H       = 22;
    private static final int LIST_BTN_GAP     = 2;
    private static final int MIN_LEFT_W       = 130;
    private static final int MAX_LEFT_W       = 200;
    private static final int PANEL_GAP        = 8;
    private static final int DETAIL_PAD       = 10;
    private static final int HEADER_H         = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10; // 76
    private static final int DOT_SIZE         = 5;
    private static final int DOT_SPACING      = 8;
    private static final int DOT_COUNT        = 4;
    private static final int LINE_H           = 10;

    private final boolean isOrb;
    private final boolean forceReselect;
    private final OriginSelectionPresenter presenter = new OriginSelectionPresenter();

    // Computed layout geometry
    private int panelX, panelBottom, leftW, rightX, rightW, listTop, listVisibleCount;
    private int detailTextW; // usable text width inside the detail panel

    // Detail panel state
    private OriginDetailViewModel detailViewModel = OriginDetailViewModel.EMPTY;
    private List<FormattedCharSequence> descLines = List.of();
    private List<List<FormattedCharSequence>> wrappedPowerDescs = List.of();
    private int detailScrollOffset = 0;
    private int detailContentH     = 0;

    // Widgets
    private final List<OriginButton> originButtons  = new ArrayList<>();
    private record VisibleHeader(int y, String label) {}
    private final List<VisibleHeader> visibleHeaders = new ArrayList<>();
    private Button confirmButton;

    public OriginSelectionScreen(boolean isOrb) {
        this(isOrb, false);
    }

    public OriginSelectionScreen(boolean isOrb, boolean forceReselect) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
        this.forceReselect = forceReselect;
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        presenter.setForceReselect(forceReselect);
        if (!presenter.init()) { onClose(); return; }
        int totalW       = Math.max(280, width - 40);
        leftW            = Mth.clamp((int)(totalW * 0.30f), MIN_LEFT_W, MAX_LEFT_W);
        panelX           = (width - totalW) / 2;
        panelBottom      = height - PANEL_BTM_MARGIN;
        rightX           = panelX + leftW + PANEL_GAP;
        rightW           = totalW - leftW - PANEL_GAP;
        detailTextW      = rightW - DETAIL_PAD * 2 - 6;
        listTop          = PANEL_TOP + SEARCH_H + SEARCH_GAP;
        listVisibleCount = Math.max(1, (panelBottom - listTop) / (LIST_BTN_H + LIST_BTN_GAP));
        presenter.buildRows();
        refreshWidgets();
        updateDetail();
    }

    private void advanceLayer() {
        detailScrollOffset = 0;
        presenter.buildRows();
        refreshWidgets();
        updateDetail();
    }

    private void selectOrigin(Identifier id) {
        presenter.select(id);
        detailScrollOffset = 0;
        originButtons.forEach(b -> b.setSelected(b.getOrigin().id().equals(id)));
        if (confirmButton != null) confirmButton.active = true;
        updateDetail();
    }

    private void confirmSelection() {
        if (presenter.selectedOriginId() == null) return;
        if (!presenter.confirm()) { onClose(); return; }
        advanceLayer();
    }

    private void updateDetail() {
        detailViewModel = OriginDetailViewModel.compute(presenter.selectedOriginId());
        if (detailViewModel.origin() != null) {
            descLines = font.split(detailViewModel.origin().description(), detailTextW);

            // Pre-wrap power descriptions
            List<String> pDescs = detailViewModel.powerDescs();
            List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
            int powerDescW = detailTextW - 8; // indent for bullet point
            for (String desc : pDescs) {
                if (desc.isEmpty()) {
                    wrapped.add(List.of());
                } else {
                    wrapped.add(font.split(Component.literal(desc), powerDescW));
                }
            }
            wrappedPowerDescs = wrapped;

            // Compute content height with wrapped lines
            detailContentH = computeContentHeight();
        } else {
            descLines = List.of();
            wrappedPowerDescs = List.of();
            detailContentH = 0;
        }
    }

    private int computeContentHeight() {
        int h = 8 + descLines.size() * LINE_H + 8; // separator + desc + gap
        List<String> pNames = detailViewModel.powerNames();
        if (!pNames.isEmpty()) {
            h += 9 + 4; // "Powers" header
            for (int i = 0; i < pNames.size(); i++) {
                h += 11; // power name line
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    h += wrappedPowerDescs.get(i).size() * LINE_H;
                }
            }
        }
        return h + 6; // bottom padding
    }

    private void refreshWidgets() {
        clearWidgets();
        originButtons.clear();
        visibleHeaders.clear();

        var search = new EditBox(font, panelX, PANEL_TOP + 1, leftW, SEARCH_H,
            Component.translatable("gui.neoorigins.search.label"));
        search.setMaxLength(64);
        search.setHint(Component.translatable("gui.neoorigins.search.hint"));
        search.setBordered(true);
        search.setValue(presenter.searchText());
        search.setResponder(text -> { if (presenter.setSearch(text)) refreshWidgets(); });
        addRenderableWidget(search);

        List<OriginListEntry> rows = presenter.filteredRows();
        int offset = presenter.listScrollOffset();
        int end    = Math.min(rows.size(), offset + listVisibleCount);
        int btnY   = listTop;
        for (int i = offset; i < end; i++) {
            OriginListEntry row = rows.get(i);
            if (row.isSectionHeader()) {
                visibleHeaders.add(new VisibleHeader(btnY, row.displayName()));
            } else {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(row.id());
                if (origin != null) {
                    final Identifier rowId = row.id();
                    var btn = new OriginButton(panelX, btnY, leftW, LIST_BTN_H, origin,
                        b -> selectOrigin(rowId));
                    btn.setSelected(rowId.equals(presenter.selectedOriginId()));
                    originButtons.add(btn);
                    addRenderableWidget(btn);
                }
            }
            btnY += LIST_BTN_H + LIST_BTN_GAP;
        }

        var layer = presenter.currentLayer();
        int cy = height - 24;
        int cx = width / 2;

        var randomBtn = Button.builder(Component.translatable("button.neoorigins.random"), b -> {
            Identifier id = presenter.randomId();
            if (id != null) selectOrigin(id);
        }).bounds(panelX, cy, 70, 20).build();
        randomBtn.visible = layer.allowRandom();
        addRenderableWidget(randomBtn);

        var backBtn = Button.builder(Component.translatable("gui.neoorigins.button.back"), b -> {
            if (presenter.back()) advanceLayer();
        }).bounds(cx - 92, cy, 80, 20).build();
        backBtn.active = presenter.currentLayerIndex() > 0;
        addRenderableWidget(backBtn);

        confirmButton = Button.builder(Component.translatable("gui.neoorigins.button.confirm"), b -> confirmSelection())
            .bounds(cx + 12, cy, 80, 20).build();
        confirmButton.active = presenter.selectedOriginId() != null;
        addRenderableWidget(confirmButton);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);
        if (presenter.isDone()) return;

        var layerTitle = Component.translatable("screen.neoorigins.choose_prompt", presenter.currentLayer().name());
        g.centeredText(font, layerTitle, width / 2, 14, 0xFFFFFFFF);
        String prog = (presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers();
        g.text(font, prog, width - 10 - font.width(prog), 26, 0xFF555577, false);

        g.fill(panelX - 1, PANEL_TOP - 1, panelX + leftW + 1, panelBottom + 1, 0xFF0E0E1C);
        g.outline(panelX - 1, PANEL_TOP - 1, leftW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        for (var vh : visibleHeaders) {
            g.fill(panelX, vh.y(), panelX + leftW, vh.y() + LIST_BTN_H, 0xFF080818);
            g.fill(panelX, vh.y() + 5, panelX + 2, vh.y() + LIST_BTN_H - 5, 0xFF334488);
            g.text(font, vh.label().toUpperCase(), panelX + 6, vh.y() + 7, 0xFF445577, false);
        }
        // Scroll hint sits above the list panel so it doesn't collide with the
        // Random / Back / Confirm button row at the bottom.
        if (getMaxListScroll() > 0) {
            var hint = Component.translatable("gui.neoorigins.hint.scroll");
            int hintY = PANEL_TOP - 10;
            g.text(font, hint, panelX, hintY, 0xFF334466, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partial);
        renderDetailPanel(g);
    }

    private void renderDetailPanel(GuiGraphicsExtractor g) {
        g.fill(rightX, PANEL_TOP, rightX + rightW, panelBottom, 0xFF09091A);
        g.outline(rightX - 1, PANEL_TOP - 1, rightW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        if (detailViewModel.origin() == null) {
            g.centeredText(font, Component.translatable("gui.neoorigins.hint.select"),
                rightX + rightW / 2, PANEL_TOP + (panelBottom - PANEL_TOP) / 2 - 4, 0xFF333355);
            return;
        }

        Origin origin = detailViewModel.origin();
        int cx = rightX + rightW / 2;
        int y  = PANEL_TOP + DETAIL_PAD;

        g.fill(cx - 16, y, cx + 16, y + 32, 0xFF0D1830);
        g.outline(cx - 16, y, 32, 32, 0xFF4A90D9);
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;
        g.centeredText(font, origin.name(), cx, y, 0xFFFFFFFF);
        y += 9 + 4;
        drawImpactRow(g, cx, y, origin.impact());

        int scrollTop    = PANEL_TOP + HEADER_H;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH  = scrollBottom - scrollTop;
        int maxScroll    = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(rightX + 1, scrollTop, rightX + rightW - 5, scrollBottom);
        int sy = scrollTop - detailScrollOffset;
        g.fill(rightX + DETAIL_PAD, sy + 3, rightX + rightW - DETAIL_PAD - 6, sy + 4, 0xFF252540);
        sy += 8;
        for (FormattedCharSequence line : descLines) {
            g.text(font, line, rightX + DETAIL_PAD, sy, 0xFF9999BB, false);
            sy += LINE_H;
        }
        sy += 8;
        List<String> pNames = detailViewModel.powerNames();
        if (!pNames.isEmpty()) {
            g.text(font, Component.translatable("gui.neoorigins.detail.powers_header"), rightX + DETAIL_PAD, sy, 0xFFCCCCDD, false);
            sy += 9 + 4;
            for (int i = 0; i < pNames.size(); i++) {
                g.fill(rightX + DETAIL_PAD, sy + 3, rightX + DETAIL_PAD + 3, sy + 6, 0xFF4A90D9);
                g.text(font, pNames.get(i), rightX + DETAIL_PAD + 8, sy, 0xFF7AACDA, false);
                sy += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                        g.text(font, dLine, rightX + DETAIL_PAD + 8, sy, 0xFF445566, false);
                        sy += LINE_H;
                    }
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barX   = rightX + rightW - 4;
            int thumbH = Math.max(14, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 2, scrollBottom, 0xFF1A1A30);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }
    }

    private void drawImpactRow(GuiGraphicsExtractor g, int cx, int y, Impact impact) {
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0     = cx - totalW / 2;
        for (int i = 0; i < DOT_COUNT; i++)
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE,
                i < impact.getDotCount() ? 0xFFFF8822 : 0xFF252540);
        Component label = Component.translatable("origins.gui.impact.impact").append(": ")
            .append(switch (impact) {
                case NONE   -> Component.translatable("origins.gui.impact.none");
                case LOW    -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH   -> Component.translatable("origins.gui.impact.high");
            });
        g.text(font, label, cx + totalW / 2 + 6, y - 1, 0xFF666688, false);
    }

    // ── Scrolling ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + leftW && my >= listTop && my <= panelBottom) {
            int next = Mth.clamp(presenter.listScrollOffset() + (sy > 0 ? -1 : 1), 0, getMaxListScroll());
            if (next != presenter.listScrollOffset()) { presenter.setListScrollOffset(next); refreshWidgets(); }
            return true;
        }
        if (mx >= rightX && mx <= rightX + rightW && my >= PANEL_TOP && my <= panelBottom) {
            int scrollAreaH = (panelBottom - 2) - (PANEL_TOP + HEADER_H);
            int maxScroll   = Math.max(0, detailContentH - scrollAreaH);
            detailScrollOffset = Mth.clamp(detailScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private int getMaxListScroll() {
        return Math.max(0, presenter.filteredRows().size() - listVisibleCount);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final net.minecraft.resources.Identifier CLASS_LAYER_ID =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("neoorigins", "class");
    private static final net.minecraft.resources.Identifier NITWIT_ORIGIN_ID =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("neoorigins", "class_nitwit");

    @Override
    public void onClose() {
        // If the player escaped out with their primary origin picked but no
        // class, auto-assign the nitwit class (no-effect default) so the
        // server sees `hadAllOrigins` flip true and runs grantAllPending.
        // Otherwise the player would sit in a half-selected state with no
        // starting_equipment items granted. See tester feedback 2026-04-22.
        var origins = ClientOriginState.getOrigins();
        boolean hasClass = origins.keySet().stream().anyMatch(CLASS_LAYER_ID::equals);
        boolean hasAnyOrigin = !origins.isEmpty();
        if (hasAnyOrigin && !hasClass) {
            ClientPacketDistributor.sendToServer(new ChooseOriginPayload(CLASS_LAYER_ID, NITWIT_ORIGIN_ID));
        }
        Minecraft.getInstance().setScreen(null);
    }
}
