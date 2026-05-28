package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginTierOverlay;
import com.cyberday1.neoorigins.client.ClientEvolutionConfig;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.theme.PanelRenderer;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.evolution.EssenceEvolutionManager;
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
            String layerName = getLayerDisplayName(layer);
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

    private String getLayerDisplayName(com.cyberday1.neoorigins.api.origin.OriginLayer layer) {
        // Prefer the explicit "name" field from the layer JSON — this is what
        // pack authors set as the user-facing label. Only fall back to the
        // translation key / capitalized path if the layer somehow has a blank
        // name (defensive — codec marks name mandatory).
        String fromField = layer.name().getString();
        if (fromField != null && !fromField.isBlank()) return fromField;
        ResourceLocation layerId = layer.id();
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
        // Evolution path section (inline, after spawn location, before powers).
        h += evolutionSectionHeight(vm);
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

    /** Pixel height of the evolution-path section. Returns 0 when there are no tier overlays. */
    private int evolutionSectionHeight(OriginDetailViewModel vm) {
        if (vm.origin() == null || vm.origin().tierPowers().isEmpty()) return 0;
        int h = 8;                  // gap before section
        // Optional "Next Evolution: X / Y" (or "Apex reached") summary line above the header.
        // Hidden entirely when evolution is disabled server-side.
        if (ClientEvolutionConfig.isEnabled()) {
            h += LINE_H;
        }
        h += 9 + 4;                 // "Evolution Path" header
        for (OriginTierOverlay overlay : vm.origin().tierPowers()) {
            h += 11;                // tier subheader ("Evolved" / "Ascended" / "Apex")
            // Per-tier progress annotation ("23 / 1000 kills" / "Achieved").
            // Only emitted when evolution is enabled.
            if (ClientEvolutionConfig.isEnabled()) {
                h += LINE_H;
            }
            h += overlay.add().size() * LINE_H;
            h += overlay.remove().size() * LINE_H;
        }
        return h;
    }

    /** Wraps a Component with the theme's font Style so a custom font provider can take effect. */
    private static net.minecraft.network.chat.Component themed(net.minecraft.network.chat.Component c) {
        ResourceLocation fid = UITheme.current().font();
        return fid != null ? c.copy().withStyle(s -> s.withFont(fid)) : c;
    }

    /** Best-effort human display name for a power id, using the same logic the detail view uses. */
    private static String powerDisplayName(ResourceLocation powerId) {
        var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(powerId);
        if (holder != null && holder.name() != null) {
            String s = holder.name().getString();
            if (!s.isEmpty()) return s;
        }
        var entry = com.cyberday1.neoorigins.client.ClientPowerCache.get(powerId);
        if (entry != null && entry.name() != null) {
            String s = entry.name().getString();
            if (!s.isEmpty()) return s;
        }
        String key = "power." + powerId.getNamespace() + "." + powerId.getPath() + ".name";
        net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
        if (lang.has(key)) return lang.getOrDefault(key, "");
        return OriginDetailViewModel.formatPowerId(powerId);
    }

    private static String tierName(int tier) {
        if (tier >= 0 && tier < EssenceEvolutionManager.TIER_NAMES.length) {
            String n = EssenceEvolutionManager.TIER_NAMES[tier];
            if (!n.isEmpty()) return n;
        }
        return "Tier " + tier;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        // Full-screen scrim — kept dark behind the parchment panel.
        g.fill(0, 0, width, height, theme.overlayColor());

        if (tabs.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.neoorigins.info.no_origin"),
                width / 2, height / 2 - 10, theme.mutedColor());
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        // Parchment panel.
        PanelRenderer.drawPanel(g, theme, panelX - 1, PANEL_TOP - 1, panelW + 2, panelBottom - PANEL_TOP + 2);

        TabEntry tab = tabs.get(currentTab);
        OriginDetailViewModel vm = tab.viewModel();
        if (vm.origin() == null) {
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        Origin origin = vm.origin();
        int cx = panelX + panelW / 2;
        int y = PANEL_TOP + DETAIL_PAD;

        g.renderOutline(cx - 16, y, 32, 32, theme.borderColor());
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;
        g.drawCenteredString(font, origin.name(), cx, y, theme.nameColor());
        y += 9 + 4;
        drawImpactRow(g, cx, y, origin.impact());

        int scrollTop = PANEL_TOP + HEADER_H;
        int scrollBottom = panelBottom - 2;
        int scrollAreaH = scrollBottom - scrollTop;
        int maxScroll = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(panelX + 1, scrollTop, panelX + panelW - 5, scrollBottom);
        int sy = scrollTop - detailScrollOffset;
        g.fill(panelX + DETAIL_PAD, sy + 3, panelX + panelW - DETAIL_PAD - 6, sy + 4, theme.borderColor());
        sy += 8;
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, panelX + DETAIL_PAD, sy, theme.descriptionColor(), false);
            sy += LINE_H;
        }
        if (origin.spawnLocation().isPresent()) {
            String spawnSummary = origin.spawnLocation().get().formatSummary();
            if (!spawnSummary.isEmpty()) {
                g.drawString(font, Component.literal(spawnSummary),
                    panelX + DETAIL_PAD, sy, theme.accentColor(), false);
                sy += LINE_H;
            }
        }

        // ── Evolution Path section ─────────────────────────────────────────
        // Renders inline between spawn-location and powers. Skipped when the
        // origin has no tier overlays.
        if (!origin.tierPowers().isEmpty()) {
            sy += 8;
            // Live progress summary line. Hidden entirely when the server
            // has evolution disabled -- the static Evolution Path section
            // still renders so players can see what *would* unlock.
            boolean evoOn = ClientEvolutionConfig.isEnabled();
            if (evoOn) {
                int curTier = ClientEvolutionConfig.getCurrentTier();
                int curKills = ClientEvolutionConfig.getCurrentKills();
                Component summary;
                if (curTier >= 3) {
                    summary = Component.translatable("gui.neoorigins.info.evolution_apex");
                } else {
                    int need = ClientEvolutionConfig.killsForTier(curTier + 1);
                    summary = Component.translatable(
                        "gui.neoorigins.info.evolution_progress",
                        String.valueOf(curKills), String.valueOf(need));
                }
                g.drawString(font, themed(summary),
                    panelX + DETAIL_PAD, sy, theme.accentColor(), false);
                sy += LINE_H;
            }
            g.drawString(font, themed(Component.translatable("gui.neoorigins.info.evolution_path")),
                panelX + DETAIL_PAD, sy, theme.headerColor(), false);
            sy += 9 + 4;
            // Sort by tier ascending so display order is Evolved → Ascended → Apex
            // even if the JSON listed them in a different order.
            var sortedTiers = new java.util.ArrayList<>(origin.tierPowers());
            sortedTiers.sort(java.util.Comparator.comparingInt(OriginTierOverlay::tier));
            int curTier = evoOn ? ClientEvolutionConfig.getCurrentTier() : -1;
            int curKills = evoOn ? ClientEvolutionConfig.getCurrentKills() : 0;
            for (OriginTierOverlay overlay : sortedTiers) {
                String name = tierName(overlay.tier());
                g.drawString(font, themed(Component.literal(name)),
                    panelX + DETAIL_PAD, sy, theme.powerNameColor(), false);
                sy += 11;
                if (evoOn) {
                    // Per-tier annotation: progress on the next-up tier,
                    // "Achieved" for tiers already reached, "Apex reached"
                    // for the top tier once attained.
                    Component annotation;
                    if (overlay.tier() <= curTier) {
                        annotation = Component.translatable("gui.neoorigins.info.evolution_tier_achieved");
                    } else if (overlay.tier() == curTier + 1) {
                        int need = ClientEvolutionConfig.killsForTier(overlay.tier());
                        annotation = Component.translatable(
                            "gui.neoorigins.info.evolution_tier_progress",
                            String.valueOf(curKills), String.valueOf(need));
                    } else {
                        int need = ClientEvolutionConfig.killsForTier(overlay.tier());
                        annotation = Component.translatable(
                            "gui.neoorigins.info.evolution_tier_progress",
                            String.valueOf(curKills), String.valueOf(need));
                    }
                    g.drawString(font, themed(annotation),
                        panelX + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                    sy += LINE_H;
                }
                for (ResourceLocation pid : overlay.add()) {
                    g.drawString(font,
                        Component.literal("+ " + powerDisplayName(pid)),
                        panelX + DETAIL_PAD + 8, sy, theme.powerDescriptionColor(), false);
                    sy += LINE_H;
                }
                for (ResourceLocation pid : overlay.remove()) {
                    g.drawString(font,
                        Component.literal("- " + powerDisplayName(pid)),
                        panelX + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                    sy += LINE_H;
                }
            }
        }

        sy += 8;
        List<String> pNames = vm.powerNames();
        if (!pNames.isEmpty()) {
            g.drawString(font, Component.translatable("gui.neoorigins.detail.powers_header"),
                panelX + DETAIL_PAD, sy, theme.headerColor(), false);
            sy += 9 + 4;
            for (int i = 0; i < pNames.size(); i++) {
                g.fill(panelX + DETAIL_PAD, sy + 3, panelX + DETAIL_PAD + 3, sy + 6, theme.accentColor());
                g.drawString(font, pNames.get(i), panelX + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
                sy += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                        g.drawString(font, dLine, panelX + DETAIL_PAD + 8, sy, theme.powerDescriptionColor(), false);
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
            g.fill(barX, scrollTop, barX + 2, scrollBottom, theme.borderColor());
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, theme.accentColor());
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void drawImpactRow(GuiGraphics g, int cx, int y, Impact impact) {
        UITheme theme = UITheme.current();
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0 = cx - totalW / 2;
        for (int i = 0; i < DOT_COUNT; i++)
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE,
                i < impact.getDotCount() ? theme.accentColor() : theme.borderColor());
        Component label = Component.translatable("origins.gui.impact.impact").append(": ")
            .append(switch (impact) {
                case NONE -> Component.translatable("origins.gui.impact.none");
                case LOW -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH -> Component.translatable("origins.gui.impact.high");
            });
        g.drawString(font, label, cx + totalW / 2 + 6, y - 1, theme.mutedColor(), false);
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
