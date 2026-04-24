package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OriginInfoScreen extends Screen {

    private static final int PANEL_TOP = 44;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int DETAIL_PAD = 10;
    private static final int HEADER_H = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10;
    private static final int DOT_SIZE = 5;
    private static final int DOT_SPACING = 8;
    private static final int DOT_COUNT = 4;
    private static final int LINE_H = 10;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;

    private record TabEntry(ResourceLocation layerId, String layerName, OriginDetailViewModel viewModel) {}

    private final List<TabEntry> tabs = new ArrayList<>();
    private int currentTab = 0;

    private int panelX, panelW, panelBottom;
    private int detailTextW;

    private List<FormattedCharSequence> descLines = List.of();
    private List<List<FormattedCharSequence>> wrappedPowerDescs = List.of();
    private int detailScrollOffset = 0;
    private int detailContentH = 0;

    public OriginInfoScreen() {
        super(Component.translatable("screen.neoorigins.origin_info"));
    }

    @Override
    protected void init() {
        tabs.clear();
        // Iterate layers in their declared order (origin layer first, then class),
        // not the raw client-state map which is alphabetical and would put Class
        // before Origin.
        Map<ResourceLocation, ResourceLocation> origins = ClientOriginState.getOrigins();
        for (var layer : com.cyberday1.neoorigins.data.LayerDataManager.INSTANCE.getSortedLayers()) {
            ResourceLocation originId = origins.get(layer.id());
            if (originId == null) continue;
            Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null) continue;
            String layerName = getLayerDisplayName(layer.id());
            tabs.add(new TabEntry(layer.id(), layerName, OriginDetailViewModel.compute(originId)));
        }

        panelW = Math.min(width - 40, 400);
        panelX = (width - panelW) / 2;
        panelBottom = height - PANEL_BTM_MARGIN;
        detailTextW = panelW - DETAIL_PAD * 2 - 6;

        currentTab = Math.min(currentTab, Math.max(0, tabs.size() - 1));

        refreshWidgets();
        updateDetail();
    }

    private String getLayerDisplayName(ResourceLocation layerId) {
        String key = "origins.layer." + layerId.getPath();
        Component c = Component.translatable(key);
        String resolved = c.getString();
        if (!resolved.equals(key)) return resolved;
        String path = layerId.getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private void refreshWidgets() {
        clearWidgets();

        if (tabs.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.close"), b -> onClose())
                .bounds(width / 2 - 40, height / 2 + 10, 80, 20).build());
            return;
        }

        int tabTotalW = 0;
        for (var tab : tabs) tabTotalW += font.width(tab.layerName()) + 16;
        tabTotalW += (tabs.size() - 1) * TAB_GAP;
        int tabX = (width - tabTotalW) / 2;
        int tabY = PANEL_TOP - TAB_H - 4;

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            String name = tabs.get(i).layerName();
            int btnW = font.width(name) + 16;
            var btn = Button.builder(Component.literal(name), b -> {
                currentTab = idx;
                detailScrollOffset = 0;
                updateDetail();
                refreshWidgets();
            }).bounds(tabX, tabY, btnW, TAB_H).build();
            btn.active = (i != currentTab);
            addRenderableWidget(btn);
            tabX += btnW + TAB_GAP;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(width / 2 - 40, height - 24, 80, 20).build());

        // Debug + Edit are dev-GUI tools — hidden unless the player is in
        // Creative. Survival players don't need the power tester or the
        // origin editor, and exposing them clutters the info screen.
        var lp = Minecraft.getInstance().player;
        boolean showDevGui = lp != null && lp.isCreative();
        if (showDevGui) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.debug"),
                    b -> Minecraft.getInstance().setScreen(new ActivePowersDebugScreen(this)))
                .bounds(width / 2 + 48, height - 24, 60, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.edit"),
                    b -> Minecraft.getInstance().setScreen(new OriginEditorScreen(this)))
                .bounds(width / 2 - 108, height - 24, 60, 20).build());
        }
    }

    private void updateDetail() {
        if (currentTab >= tabs.size()) {
            descLines = List.of();
            wrappedPowerDescs = List.of();
            detailContentH = 0;
            return;
        }
        TabEntry tab = tabs.get(currentTab);
        OriginDetailViewModel vm = tab.viewModel();
        if (vm.origin() != null) {
            descLines = font.split(vm.origin().description(), detailTextW);
            List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
            int powerDescW = detailTextW - 8;
            for (String desc : vm.powerDescs()) {
                wrapped.add(desc.isEmpty() ? List.of() : font.split(Component.literal(desc), powerDescW));
            }
            wrappedPowerDescs = wrapped;
            detailContentH = computeContentHeight(vm);
        } else {
            descLines = List.of();
            wrappedPowerDescs = List.of();
            detailContentH = 0;
        }
    }

    private int computeContentHeight(OriginDetailViewModel vm) {
        int h = 8 + descLines.size() * LINE_H + 8;
        if (vm.origin() != null && vm.origin().spawnLocation().isPresent()
            && !vm.origin().spawnLocation().get().formatSummary().isEmpty()) {
            h += LINE_H;
        }
        if (!vm.powerNames().isEmpty()) {
            h += 9 + 4;
            for (int i = 0; i < vm.powerNames().size(); i++) {
                h += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    h += wrappedPowerDescs.get(i).size() * LINE_H;
                }
            }
        }
        return h + 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xCC060610);

        if (tabs.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.neoorigins.info.no_origin"),
                width / 2, height / 2 - 10, 0xFF555577);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        g.fill(panelX, PANEL_TOP, panelX + panelW, panelBottom, 0xFF09091A);
        g.renderOutline(panelX - 1, PANEL_TOP - 1, panelW + 2, panelBottom - PANEL_TOP + 2, 0xFF252540);

        TabEntry tab = tabs.get(currentTab);
        OriginDetailViewModel vm = tab.viewModel();
        if (vm.origin() == null) {
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        Origin origin = vm.origin();
        int cx = panelX + panelW / 2;
        int y = PANEL_TOP + DETAIL_PAD;

        g.fill(cx - 16, y, cx + 16, y + 32, 0xFF0D1830);
        g.renderOutline(cx - 16, y, 32, 32, 0xFF4A90D9);
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;
        g.drawCenteredString(font, origin.name(), cx, y, 0xFFFFFFFF);
        y += 9 + 4;
        drawImpactRow(g, cx, y, origin.impact());

        int scrollTop = PANEL_TOP + HEADER_H;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH = scrollBottom - scrollTop;
        int maxScroll = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(panelX + 1, scrollTop, panelX + panelW - 5, scrollBottom);
        int sy = scrollTop - detailScrollOffset;
        g.fill(panelX + DETAIL_PAD, sy + 3, panelX + panelW - DETAIL_PAD - 6, sy + 4, 0xFF252540);
        sy += 8;
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, panelX + DETAIL_PAD, sy, 0xFF9999BB, false);
            sy += LINE_H;
        }
        if (origin.spawnLocation().isPresent()) {
            String spawnSummary = origin.spawnLocation().get().formatSummary();
            if (!spawnSummary.isEmpty()) {
                g.drawString(font, Component.literal(spawnSummary),
                    panelX + DETAIL_PAD, sy, 0xFFFFAA55, false);
                sy += LINE_H;
            }
        }
        sy += 8;
        List<String> pNames = vm.powerNames();
        if (!pNames.isEmpty()) {
            g.drawString(font, Component.translatable("gui.neoorigins.detail.powers_header"),
                panelX + DETAIL_PAD, sy, 0xFFCCCCDD, false);
            sy += 9 + 4;
            for (int i = 0; i < pNames.size(); i++) {
                g.fill(panelX + DETAIL_PAD, sy + 3, panelX + DETAIL_PAD + 3, sy + 6, 0xFF4A90D9);
                g.drawString(font, pNames.get(i), panelX + DETAIL_PAD + 8, sy, 0xFF7AACDA, false);
                sy += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                        g.drawString(font, dLine, panelX + DETAIL_PAD + 8, sy, 0xFF445566, false);
                        sy += LINE_H;
                    }
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barX = panelX + panelW - 4;
            int thumbH = Math.max(14, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 2, scrollBottom, 0xFF1A1A30);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF4A90D9);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawImpactRow(GuiGraphics g, int cx, int y, Impact impact) {
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0 = cx - totalW / 2;
        for (int i = 0; i < DOT_COUNT; i++)
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE,
                i < impact.getDotCount() ? 0xFFFF8822 : 0xFF252540);
        Component label = Component.translatable("origins.gui.impact.impact").append(": ")
            .append(switch (impact) {
                case NONE -> Component.translatable("origins.gui.impact.none");
                case LOW -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH -> Component.translatable("origins.gui.impact.high");
            });
        g.drawString(font, label, cx + totalW / 2 + 6, y - 1, 0xFF666688, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + panelW && my >= PANEL_TOP && my <= panelBottom) {
            int scrollAreaH = (panelBottom - 2) - (PANEL_TOP + HEADER_H);
            int maxScroll = Math.max(0, detailContentH - scrollAreaH);
            detailScrollOffset = Mth.clamp(detailScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No-op: disable background blur so our semi-transparent fill is visible
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
