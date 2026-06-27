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
    /** Width of the 9-slice burnt-edge curl in the parchment panel — widgets
     *  that touch the panel edge should inset by this amount. */
    private static final int PANEL_INSET = 12;
    private static final int DETAIL_PAD = 10;
    private static final int HEADER_H = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10;
    private static final int DOT_SIZE = 5;
    private static final int DOT_SPACING = 8;
    private static final int DOT_COUNT = 4;
    private static final int LINE_H = 10;
    /** Vertical gap between consecutive power entries in the detail panel. */
    private static final int POWER_GAP = 5;
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;

    /**
     * One tab per origin layer. Holds the full set of browseable origins in
     * that layer (not just the player's own) so the info screen can page
     * through every origin, defaulting to the player's current pick.
     */
    private static final class TabEntry {
        final ResourceLocation layerId;
        final String layerName;
        final boolean classLayer;
        final List<ResourceLocation> originIds;
        final int ownIndex;
        int browseIndex;
        TabEntry(ResourceLocation layerId, String layerName, boolean classLayer,
                 List<ResourceLocation> originIds, int ownIndex) {
            this.layerId = layerId;
            this.layerName = layerName;
            this.classLayer = classLayer;
            this.originIds = originIds;
            this.ownIndex = ownIndex;
            this.browseIndex = ownIndex;
        }
        String layerName()                 { return layerName; }
        ResourceLocation currentOriginId() { return originIds.get(browseIndex); }
        boolean viewingOwn()               { return browseIndex == ownIndex; }
        int total()                        { return originIds.size(); }
    }

    private static final ResourceLocation CLASS_LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class");

    private static final int NAV_W = 14;

    private final List<TabEntry> tabs = new ArrayList<>();
    /** Browse position per layer, persisted across init()/resize. */
    private final Map<ResourceLocation, Integer> browseMemory = new java.util.HashMap<>();
    /** Detail view for the currently-browsed origin (recomputed on navigation). */
    private OriginDetailViewModel currentVm;
    private int currentTab = 0;

    private int panelX, panelW, panelBottom;
    private int detailTextW;

    private List<FormattedCharSequence> descLines = List.of();
    private List<List<FormattedCharSequence>> wrappedPowerDescs = List.of();
    /** Precomputed evolution-path display: per tier, the added powers (name +
     *  wrapped description lines) and the removed power names. Synthetic
     *  multiple-power sub-ids are collapsed back to their parent display. */
    private record EvoLine(String name, List<FormattedCharSequence> desc) {}
    private record EvoTier(int tier, List<EvoLine> added, List<String> removed) {}
    private List<EvoTier> evoTiers = List.of();
    /** True when the browsed origin is the player's own pick for the current layer. */
    private boolean viewingOwn = true;
    private int detailScrollOffset = 0;
    private int detailContentH = 0;
    // Scrollbar thumb drag state
    private boolean draggingThumb = false;
    private double dragGrabY = 0;

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
            boolean classLayer = CLASS_LAYER_ID.equals(layer.id());
            List<ResourceLocation> ids = orderedOriginIds(layer, originId);
            int ownIndex = Math.max(0, ids.indexOf(originId));
            TabEntry tab = new TabEntry(layer.id(), layerName, classLayer, ids, ownIndex);
            // Restore any remembered browse position (survives resize), clamped
            // to the rebuilt list in case the available set changed.
            Integer remembered = browseMemory.get(layer.id());
            if (remembered != null) tab.browseIndex = Mth.clamp(remembered, 0, ids.size() - 1);
            tabs.add(tab);
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

    /**
     * The ordered set of origins the player may browse in a layer: every
     * available, content-enabled, choosable origin, neoorigins first then by
     * namespace, alphabetical within. The player's own current origin is always
     * forced in (even if it became unchoosable) so the default view is valid.
     */
    private List<ResourceLocation> orderedOriginIds(
            com.cyberday1.neoorigins.api.origin.OriginLayer layer, ResourceLocation ownId) {
        Map<ResourceLocation, ResourceLocation> choices = ClientOriginState.getOrigins();
        List<ResourceLocation> ids = new ArrayList<>();
        for (var co : layer.origins()) {
            if (!co.isAvailable(choices)) continue;
            if (com.cyberday1.neoorigins.config.ContentTogglesConfig.isOriginDisabled(co.origin())) continue;
            if (!OriginDataManager.INSTANCE.hasOrigin(co.origin())) continue;
            Origin o = OriginDataManager.INSTANCE.getOrigin(co.origin());
            if (o != null && o.unchoosable()) continue;
            if (!ids.contains(co.origin())) ids.add(co.origin());
        }
        ids.sort((a, b) -> {
            boolean na = "neoorigins".equals(a.getNamespace());
            boolean nb = "neoorigins".equals(b.getNamespace());
            if (na != nb) return na ? -1 : 1;
            int ns = a.getNamespace().compareToIgnoreCase(b.getNamespace());
            if (ns != 0) return ns;
            return originName(a).compareToIgnoreCase(originName(b));
        });
        if (ownId != null && !ids.contains(ownId)) ids.add(0, ownId);
        return ids;
    }

    private static String originName(ResourceLocation id) {
        Origin o = OriginDataManager.INSTANCE.getOrigin(id);
        return o != null ? o.name().getString() : id.getPath();
    }

    /** Step the browsed origin within the current layer (wraps around). */
    private void browse(int delta) {
        if (tabs.isEmpty()) return;
        TabEntry tab = tabs.get(currentTab);
        int n = tab.total();
        if (n <= 1) return;
        tab.browseIndex = Math.floorMod(tab.browseIndex + delta, n);
        browseMemory.put(tab.layerId, tab.browseIndex);
        detailScrollOffset = 0;
        updateDetail();
        refreshWidgets();
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

        // Prev/next origin browse arrows — only when the current layer has more
        // than one browseable origin. They flank the centered origin icon at the
        // top of the parchment panel.
        if (currentTab < tabs.size() && tabs.get(currentTab).total() > 1) {
            int navY = PANEL_TOP + DETAIL_PAD + 8;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> browse(-1))
                .bounds(panelX + PANEL_INSET, navY, NAV_W, TAB_H).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> browse(1))
                .bounds(panelX + panelW - PANEL_INSET - NAV_W, navY, NAV_W, TAB_H).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.close"), b -> onClose())
            .bounds(width / 2 - 40, height - 24, 80, 20).build());

        // Debug + Edit are dev-GUI tools — hidden unless the player is in
        // Creative. Survival players don't need the power tester or the
        // origin editor, and exposing them clutters the info screen.
        var lp = Minecraft.getInstance().player;
        boolean creative = lp != null && lp.isCreative();
        if (creative) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.debug"),
                    b -> Minecraft.getInstance().setScreen(new ActivePowersDebugScreen(this)))
                .bounds(width / 2 + 48, height - 24, 60, 20).build());
        }
        // The editor can additionally be ungated for all game modes via config
        // (ui.show_origin_editor) for pack authors who build origins in survival.
        if (creative || com.cyberday1.neoorigins.client.NeoOriginsClientConfig.isShowOriginEditor()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.edit"),
                    b -> Minecraft.getInstance().setScreen(new OriginEditorScreen(this)))
                .bounds(width / 2 - 108, height - 24, 60, 20).build());
        }
    }

    private void updateDetail() {
        if (currentTab >= tabs.size()) {
            currentVm = null;
            viewingOwn = true;
            descLines = List.of();
            wrappedPowerDescs = List.of();
            evoTiers = List.of();
            detailContentH = 0;
            return;
        }
        TabEntry tab = tabs.get(currentTab);
        viewingOwn = tab.viewingOwn();
        currentVm = OriginDetailViewModel.compute(tab.currentOriginId(), tab.classLayer);
        OriginDetailViewModel vm = currentVm;
        if (vm.origin() != null) {
            // Wrap with themed() BEFORE Font.split — the split bakes Style into
            // the FormattedCharSequence so the font selector has to be on the
            // Component before this point. See OriginSelectionScreen.updateDetail.
            descLines = font.split(themed(vm.origin().description()), detailTextW);
            List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
            int powerDescW = detailTextW - 8;
            for (String desc : vm.powerDescs()) {
                wrapped.add(desc.isEmpty() ? List.of() : font.split(themed(Component.literal(desc)), powerDescW));
            }
            wrappedPowerDescs = wrapped;
            evoTiers = computeEvoTiers(vm, powerDescW);
            detailContentH = computeContentHeight(vm);
        } else {
            descLines = List.of();
            wrappedPowerDescs = List.of();
            evoTiers = List.of();
            detailContentH = 0;
        }
    }

    private int computeContentHeight(OriginDetailViewModel vm) {
        int h = 8 + descLines.size() * LINE_H + 8;
        if (vm.origin() != null && vm.origin().spawnLocation().isPresent()
            && !vm.origin().spawnLocation().get().formatSummary().isEmpty()) {
            h += LINE_H;
        }
        // Evolution path section (rendered after the powers list).
        h += evolutionSectionHeight(vm);
        if (!vm.powerNames().isEmpty()) {
            h += 9 + 4;
            for (int i = 0; i < vm.powerNames().size(); i++) {
                h += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    h += wrappedPowerDescs.get(i).size() * LINE_H;
                }
                h += POWER_GAP;
            }
        }
        return h + 6;
    }

    /** Pixel height of the evolution-path section. Returns 0 when there are no tier overlays. */
    private int evolutionSectionHeight(OriginDetailViewModel vm) {
        if (vm.origin() == null || vm.origin().tierPowers().isEmpty()) return 0;
        int h = 8;                  // gap before section
        // Live progress lines only make sense for the player's OWN origin —
        // when browsing another origin we show just the static evolution path.
        boolean evoOn = ClientEvolutionConfig.isEnabled() && viewingOwn;
        // Optional "Next Evolution: X / Y" (or "Apex reached") summary line above the header.
        // Hidden entirely when evolution is disabled server-side.
        if (evoOn) {
            h += LINE_H;
        }
        h += 9 + 4;                 // "Evolution Path" header
        for (EvoTier tier : evoTiers) {
            h += 11;                // tier subheader ("Evolved" / "Ascended" / "Apex")
            // Per-tier progress annotation ("23 / 1000 kills" / "Achieved").
            // Only emitted when evolution is enabled.
            if (evoOn) {
                h += LINE_H;
            }
            for (EvoLine line : tier.added()) {
                h += LINE_H;                          // "+ Name"
                h += line.desc().size() * LINE_H;     // wrapped description
            }
            h += tier.removed().size() * LINE_H;      // "- Name"
        }
        return h;
    }

    /** Build the per-tier evolution display, collapsing synthetic multiple-power
     *  sub-ids back to their parent's name/description and wrapping the
     *  descriptions of added powers to {@code descW}. Tiers are sorted ascending
     *  so the display order is Evolved → Ascended → Apex. */
    private List<EvoTier> computeEvoTiers(OriginDetailViewModel vm, int descW) {
        if (vm.origin() == null || vm.origin().tierPowers().isEmpty()) return List.of();
        var sorted = new ArrayList<>(vm.origin().tierPowers());
        sorted.sort(java.util.Comparator.comparingInt(OriginTierOverlay::tier));
        List<EvoTier> out = new ArrayList<>();
        for (OriginTierOverlay overlay : sorted) {
            List<EvoLine> added = new ArrayList<>();
            for (var d : OriginDetailViewModel.resolveTierPowerDisplays(overlay.add())) {
                List<FormattedCharSequence> desc = d.description().isEmpty()
                    ? List.of()
                    : font.split(themed(Component.literal(d.description())), descW);
                added.add(new EvoLine(d.name(), desc));
            }
            List<String> removed = new ArrayList<>();
            for (var d : OriginDetailViewModel.resolveTierPowerDisplays(overlay.remove())) {
                removed.add(d.name());
            }
            out.add(new EvoTier(overlay.tier(), added, removed));
        }
        return out;
    }

    /** Wraps a Component with the theme's font Style so a custom font provider can take effect. */
    private static net.minecraft.network.chat.Component themed(net.minecraft.network.chat.Component c) {
        ResourceLocation fid = UITheme.current().font();
        return fid != null ? c.copy().withStyle(s -> s.withFont(fid)) : c;
    }

    /** Like {@link #themed} but also marks the Style as bold — used for the
     *  origin-name header and per-power name lines so the TTF renderer picks
     *  up its synthesized bold weight. */
    private static net.minecraft.network.chat.Component themedBold(net.minecraft.network.chat.Component c) {
        ResourceLocation fid = UITheme.current().font();
        return c.copy().withStyle(s -> {
            var styled = s.withBold(true);
            return fid != null ? styled.withFont(fid) : styled;
        });
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
            var msg = themed(Component.translatable("gui.neoorigins.info.no_origin"));
            g.drawString(font, msg, width / 2 - font.width(msg) / 2,
                height / 2 - 10, theme.mutedColor(), false);
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        // Parchment panel.
        PanelRenderer.drawPanel(g, theme, panelX - 1, PANEL_TOP - 1, panelW + 2, panelBottom - PANEL_TOP + 2);

        OriginDetailViewModel vm = currentVm;
        if (vm == null || vm.origin() == null) {
            super.render(g, mouseX, mouseY, partial);
            return;
        }

        Origin origin = vm.origin();
        int cx = panelX + panelW / 2;
        int y = PANEL_TOP + DETAIL_PAD;

        g.renderOutline(cx - 16, y, 32, 32, theme.borderColor());
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;
        var nameC = themedBold(origin.name());
        g.drawString(font, nameC, cx - font.width(nameC) / 2, y, theme.nameColor(), false);
        y += 9 + 4;
        drawImpactRow(g, cx, y, origin.impact());

        // Browse position + ownership marker, shown in the header gutter just
        // above the scroll area when the layer has more than one origin.
        TabEntry curTab = tabs.get(currentTab);
        if (curTab.total() > 1) {
            Component posC = viewingOwn
                ? Component.translatable("gui.neoorigins.info.your_origin")
                    .append(Component.literal("  " + (curTab.browseIndex + 1) + " / " + curTab.total()))
                : Component.literal((curTab.browseIndex + 1) + " / " + curTab.total());
            var themedPos = themed(posC);
            g.drawString(font, themedPos, cx - font.width(themedPos) / 2,
                PANEL_TOP + HEADER_H - 9, viewingOwn ? theme.accentColor() : theme.mutedColor(), false);
        }

        int scrollTop = PANEL_TOP + HEADER_H;
        // Pull the bottom in so the rail clears the parchment burnt-edge curl.
        int scrollBottom = panelBottom - PANEL_INSET;
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
                g.drawString(font, themed(Component.literal(spawnSummary)),
                    panelX + DETAIL_PAD, sy, theme.accentColor(), false);
                sy += LINE_H;
            }
        }

        sy += 8;
        List<String> pNames = vm.powerNames();
        if (!pNames.isEmpty()) {
            g.drawString(font, themedBold(Component.translatable("gui.neoorigins.detail.powers_header")),
                panelX + DETAIL_PAD, sy, theme.headerColor(), false);
            sy += 9 + 4;
            for (int i = 0; i < pNames.size(); i++) {
                g.fill(panelX + DETAIL_PAD, sy + 3, panelX + DETAIL_PAD + 3, sy + 6, theme.accentColor());
                var pNameC = themedBold(Component.literal(pNames.get(i)));
                g.drawString(font, pNameC, panelX + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
                // Hotkey tag (e.g. "[R]") — same slot logic as the HUD cluster.
                List<String> pTags = vm.powerKeyTags();
                if (i < pTags.size() && !pTags.get(i).isEmpty()) {
                    g.drawString(font, themed(Component.literal(pTags.get(i))),
                        panelX + DETAIL_PAD + 8 + font.width(pNameC) + 5, sy, theme.accentColor(), false);
                }
                sy += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                        g.drawString(font, dLine, panelX + DETAIL_PAD + 8, sy, theme.powerDescriptionColor(), false);
                        sy += LINE_H;
                    }
                }
                sy += POWER_GAP;
            }
        }

        // ── Evolution Path section ─────────────────────────────────────────
        // Renders below the powers list so the base kit reads first, then how
        // it grows with evolution. Skipped when the origin has no tier overlays.
        if (!origin.tierPowers().isEmpty()) {
            sy += 8;
            // Live progress summary line. Hidden entirely when the server
            // has evolution disabled, or when browsing another origin (the
            // kill counts are the player's own) -- the static Evolution Path
            // section still renders so players can see what *would* unlock.
            boolean evoOn = ClientEvolutionConfig.isEnabled() && viewingOwn;
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
            int curTier = evoOn ? ClientEvolutionConfig.getCurrentTier() : -1;
            int curKills = evoOn ? ClientEvolutionConfig.getCurrentKills() : 0;
            for (EvoTier tier : evoTiers) {
                String name = tierName(tier.tier());
                g.drawString(font, themed(Component.literal(name)),
                    panelX + DETAIL_PAD, sy, theme.powerNameColor(), false);
                sy += 11;
                if (evoOn) {
                    // Per-tier annotation: progress on the next-up tier,
                    // "Achieved" for tiers already reached, "Apex reached"
                    // for the top tier once attained.
                    Component annotation;
                    if (tier.tier() <= curTier) {
                        annotation = Component.translatable("gui.neoorigins.info.evolution_tier_achieved");
                    } else {
                        int need = ClientEvolutionConfig.killsForTier(tier.tier());
                        annotation = Component.translatable(
                            "gui.neoorigins.info.evolution_tier_progress",
                            String.valueOf(curKills), String.valueOf(need));
                    }
                    g.drawString(font, themed(annotation),
                        panelX + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                    sy += LINE_H;
                }
                for (EvoLine line : tier.added()) {
                    g.drawString(font,
                        themed(Component.literal("+ " + line.name())),
                        panelX + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
                    sy += LINE_H;
                    for (FormattedCharSequence dl : line.desc()) {
                        g.drawString(font, dl,
                            panelX + DETAIL_PAD + 16, sy, theme.powerDescriptionColor(), false);
                        sy += LINE_H;
                    }
                }
                for (String rname : tier.removed()) {
                    g.drawString(font,
                        themed(Component.literal("- " + rname)),
                        panelX + DETAIL_PAD + 8, sy, theme.mutedColor(), false);
                    sy += LINE_H;
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            // Sit the scroll rail inside the parchment burnt-edge curl (12px)
            // so it doesn't run off the curled paper border.
            int barX = panelX + panelW - 12;
            int thumbH = Math.max(10, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 1, scrollBottom, theme.borderColor());
            g.fill(barX, thumbY, barX + 1, thumbY + thumbH, theme.accentColor());
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
        g.drawString(font, themed(label), cx + totalW / 2 + 6, y - 1, theme.mutedColor(), false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left/right arrows page through the origins in the current layer.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { browse(-1); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { browse(1);  return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + panelW && my >= PANEL_TOP && my <= panelBottom) {
            int scrollAreaH = (panelBottom - PANEL_INSET) - (PANEL_TOP + HEADER_H);
            int maxScroll = Math.max(0, detailContentH - scrollAreaH);
            detailScrollOffset = Mth.clamp(detailScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int scrollTop = PANEL_TOP + HEADER_H;
            int scrollBottom = panelBottom - PANEL_INSET;
            int scrollAreaH = scrollBottom - scrollTop;
            int maxScroll = Math.max(0, detailContentH - scrollAreaH);
            if (maxScroll > 0) {
                int barX = panelX + panelW - PANEL_INSET;
                int thumbH = Math.max(10, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
                int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
                // Widen the clickable rail by a few px so the 1px-wide bar is grabbable.
                if (mx >= barX - 3 && mx <= barX + 4 && my >= scrollTop && my <= scrollBottom) {
                    if (my >= thumbY && my <= thumbY + thumbH) {
                        // Grab the thumb where the user clicked.
                        draggingThumb = true;
                        dragGrabY = my - thumbY;
                    } else {
                        // Track click: center the thumb on the cursor and start dragging.
                        draggingThumb = true;
                        dragGrabY = thumbH / 2.0;
                        setScrollFromThumbTop(my - dragGrabY, scrollTop, scrollAreaH, thumbH, maxScroll);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingThumb && button == 0) {
            int scrollTop = PANEL_TOP + HEADER_H;
            int scrollBottom = panelBottom - PANEL_INSET;
            int scrollAreaH = scrollBottom - scrollTop;
            int maxScroll = Math.max(0, detailContentH - scrollAreaH);
            if (maxScroll > 0) {
                int thumbH = Math.max(10, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
                setScrollFromThumbTop(my - dragGrabY, scrollTop, scrollAreaH, thumbH, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingThumb) {
            draggingThumb = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Inverse of the render-side thumbY formula: map a desired thumb-top pixel
     *  back to a scroll offset, clamped to the valid range. */
    private void setScrollFromThumbTop(double thumbTop, int scrollTop, int scrollAreaH, int thumbH, int maxScroll) {
        int travel = scrollAreaH - thumbH;
        if (travel <= 0) { detailScrollOffset = 0; return; }
        double frac = Mth.clamp((thumbTop - scrollTop) / travel, 0.0, 1.0);
        detailScrollOffset = (int) Math.round(frac * maxScroll);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // No-op: disable background blur so our semi-transparent fill is visible
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
