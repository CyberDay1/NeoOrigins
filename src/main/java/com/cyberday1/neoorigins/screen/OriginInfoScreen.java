package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientEvolutionConfig;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.detail.OriginDetailPanel;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    private static final int TAB_H = 20;
    private static final int TAB_GAP = 4;

    /**
     * One tab per origin layer. Holds the full set of browseable origins in
     * that layer (not just the player's own) so the info screen can page
     * through every origin, defaulting to the player's current pick.
     */
    private static final class TabEntry {
        final Identifier layerId;
        final String layerName;
        final boolean classLayer;
        final List<Identifier> originIds;
        final int ownIndex;
        int browseIndex;
        TabEntry(Identifier layerId, String layerName, boolean classLayer,
                 List<Identifier> originIds, int ownIndex) {
            this.layerId = layerId;
            this.layerName = layerName;
            this.classLayer = classLayer;
            this.originIds = originIds;
            this.ownIndex = ownIndex;
            this.browseIndex = ownIndex;
        }
        String layerName()           { return layerName; }
        Identifier currentOriginId() { return originIds.get(browseIndex); }
        boolean viewingOwn()         { return browseIndex == ownIndex; }
        int total()                  { return originIds.size(); }
    }

    private static final Identifier CLASS_LAYER_ID =
        Identifier.fromNamespaceAndPath("neoorigins", "class");

    private static final int NAV_W = 14;

    private final List<TabEntry> tabs = new ArrayList<>();
    /** Browse position per layer, persisted across init()/resize. */
    private final Map<Identifier, Integer> browseMemory = new java.util.HashMap<>();
    /** Detail view for the currently-browsed origin (recomputed on navigation). */
    private OriginDetailViewModel currentVm;
    private int currentTab = 0;

    // Computed layout
    private int panelX, panelW, panelBottom;

    /** True when the browsed origin is the player's own pick for the current layer. */
    private boolean viewingOwn = true;

    /** Shared detail view. Built in init() — Screen.font is null until then. */
    private OriginDetailPanel detail;

    /**
     * Live kill progress, shown only for the player's OWN origin: the counts
     * are theirs, so browsing someone else's origin gets the static path only.
     */
    private final OriginDetailPanel.EvolutionProgress liveProgress = new OriginDetailPanel.EvolutionProgress() {
        @Override public boolean enabled()          { return ClientEvolutionConfig.isEnabled() && viewingOwn; }
        @Override public int currentTier()          { return ClientEvolutionConfig.getCurrentTier(); }
        @Override public int currentKills()         { return ClientEvolutionConfig.getCurrentKills(); }
        @Override public int killsForTier(int tier) { return ClientEvolutionConfig.killsForTier(tier); }
    };

    public OriginInfoScreen() {
        super(Component.translatable("screen.neoorigins.origin_info"));
    }

    @Override
    protected void init() {
        tabs.clear();
        // Iterate layers in their declared order (origin layer first, then class),
        // not the raw client-state map which is alphabetical and would put Class
        // before Origin.
        Map<Identifier, Identifier> origins = ClientOriginState.getOrigins();
        for (var layer : com.cyberday1.neoorigins.data.LayerDataManager.INSTANCE.getSortedLayers()) {
            Identifier originId = origins.get(layer.id());
            if (originId == null) continue;
            Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null) continue;
            String layerName = getLayerDisplayName(layer);
            boolean classLayer = CLASS_LAYER_ID.equals(layer.id());
            List<Identifier> ids = orderedOriginIds(layer, originId);
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

        // Screen.font is only populated by the time init() runs, so the panel
        // cannot be a field initialiser.
        detail = new OriginDetailPanel(font);
        detail.setBounds(panelX, PANEL_TOP, panelW, panelBottom - PANEL_TOP);
        detail.setEvolutionProgress(liveProgress);

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
        Identifier layerId = layer.id();
        String key = "origins.layer." + layerId.getPath();
        Component c = Component.translatable(key);
        String resolved = c.getString();
        if (!resolved.equals(key)) return resolved;
        // Fallback: capitalize path
        String path = layerId.getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /**
     * The ordered set of origins the player may browse in a layer: every
     * available, content-enabled, choosable origin, neoorigins first then by
     * namespace, alphabetical within. The player's own current origin is always
     * forced in (even if it became unchoosable) so the default view is valid.
     */
    private List<Identifier> orderedOriginIds(
            com.cyberday1.neoorigins.api.origin.OriginLayer layer, Identifier ownId) {
        Map<Identifier, Identifier> choices = ClientOriginState.getOrigins();
        List<Identifier> ids = new ArrayList<>();
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

    private static String originName(Identifier id) {
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

        // Tab buttons
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

        // Close button
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
        }
        // The editor can additionally be ungated for all game modes via config
        // (ui.show_origin_editor) for pack authors who build origins in survival.
        if (showDevGui || com.cyberday1.neoorigins.client.NeoOriginsClientConfig.isShowOriginEditor()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neoorigins.info.edit"),
                    b -> Minecraft.getInstance().setScreen(new OriginEditorScreen(this)))
                .bounds(width / 2 - 108, height - 24, 60, 20).build());
        }
    }

    private void updateDetail() {
        if (currentTab >= tabs.size()) {
            currentVm = null;
            viewingOwn = true;
            detail.clear();
            return;
        }
        TabEntry tab = tabs.get(currentTab);
        // viewingOwn gates liveProgress, so it must be set before setOrigin()
        // measures the evolution section.
        viewingOwn = tab.viewingOwn();
        currentVm = OriginDetailViewModel.compute(tab.currentOriginId(), tab.classLayer);
        detail.setOrigin(currentVm);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        // Full-screen scrim — kept dark behind the parchment panel.
        g.fill(0, 0, width, height, theme.overlayColor());

        if (tabs.isEmpty()) {
            var msg = UIThemeUtils.themed(Component.translatable("gui.neoorigins.info.no_origin"));
            g.text(font, msg, width / 2 - font.width(msg) / 2,
                height / 2 - 10, theme.mutedColor(), false);
            super.extractRenderState(g, mouseX, mouseY, partial);
            return;
        }

        detail.renderHeader(g);

        // Browse position + ownership marker, drawn between the panel's header
        // and its scroll area when the layer has more than one origin.
        TabEntry curTab = tabs.get(currentTab);
        if (currentVm != null && currentVm.origin() != null && curTab.total() > 1) {
            int cx = panelX + panelW / 2;
            Component posC = viewingOwn
                ? Component.translatable("gui.neoorigins.info.your_origin")
                    .append(Component.literal("  " + (curTab.browseIndex + 1) + " / " + curTab.total()))
                : Component.literal((curTab.browseIndex + 1) + " / " + curTab.total());
            var themedPos = UIThemeUtils.themed(posC);
            g.text(font, themedPos, cx - font.width(themedPos) / 2,
                PANEL_TOP + detail.headerHeight() - 9,
                viewingOwn ? theme.accentColor() : theme.mutedColor(), false);
        }

        detail.renderBody(g);

        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Left/right arrows page through the origins in the current layer.
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { browse(-1); return true; }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { browse(1);  return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (detail.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromWidget) {
        if (detail.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, fromWidget);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (detail.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (detail.mouseReleased(event.x(), event.y(), event.button())) return true;
        return super.mouseReleased(event);
    }

    @Override protected void extractBlurredBackground(GuiGraphicsExtractor g) { /* no blur */ }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
