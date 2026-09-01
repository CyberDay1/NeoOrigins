package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.screen.detail.OriginDetailPanel;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One origin at a time, {@code <} / {@code >} to page — the layout selected by
 * {@code ui.picker_layout = CAROUSEL}. Shaped like the original Origins mod's
 * chooser, but drawn in the active {@link UITheme} rather than as a copy of it.
 *
 * <p>Browsing <em>is</em> selecting: every step calls
 * {@link OriginSelectionPresenter#select}, so Confirm always applies to whatever
 * is on screen.
 */
public class OriginCarouselSelectionScreen extends Screen implements PickerScreen {

    private static final int PANEL_TOP        = 44;
    private static final int PANEL_BTM_MARGIN = 32;
    /** Clears the 9-slice burnt-edge curl on the parchment panel. */
    private static final int PANEL_INSET      = 12;
    private static final int DETAIL_PAD       = 10;
    private static final int NAV_W            = 14;
    private static final int NAV_H            = 20;
    private static final int SEARCH_H         = 16;

    private final boolean isOrb;
    private final boolean forceReselect;
    private final List<ResourceLocation> scopedLayers;
    private final OriginSelectionPresenter presenter = new OriginSelectionPresenter();

    /** Sort choice survives close → reopen within a session, as on the two-panel screen. */
    private static OriginSelectionPresenter.SortMode lastSortMode = null;

    private int panelX, panelW, panelBottom;

    /** Built in init() — Screen.font is null until then. */
    private OriginDetailPanel detail;
    private Button confirmButton;

    /**
     * The pageable origins, flattened from {@link OriginSelectionPresenter#filteredRows()}
     * with section headers dropped. Deliberately not {@code allOriginIds()}: the
     * search filter is only applied to the filtered rows, so paging the raw id
     * list would silently ignore whatever the player typed.
     */
    private List<ResourceLocation> browseIds = List.of();
    private int browseIndex = 0;

    public OriginCarouselSelectionScreen(boolean isOrb, boolean forceReselect,
                                         List<ResourceLocation> scopedLayers) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
        this.forceReselect = forceReselect;
        this.scopedLayers = scopedLayers == null ? List.of() : List.copyOf(scopedLayers);
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        presenter.setScopedLayers(scopedLayers);
        presenter.setForceReselect(forceReselect);
        if (lastSortMode == null) {
            lastSortMode = com.cyberday1.neoorigins.client.NeoOriginsClientConfig.defaultSortMode();
        }
        presenter.setSortMode(lastSortMode);
        detail = new OriginDetailPanel(font);
        boolean hasPending = presenter.init();
        if (!hasPending) { onClose(); return; }

        panelW      = Math.min(width - 40, 400);
        panelX      = (width - panelW) / 2;
        panelBottom = height - PANEL_BTM_MARGIN;
        detail.setBounds(panelX, PANEL_TOP, panelW, panelBottom - PANEL_TOP);

        presenter.buildRows();
        rebuildBrowseIds(false);
        refreshWidgets();
    }

    private void rebuildBrowseIds(boolean keepSelection) {
        ResourceLocation prev = keepSelection ? presenter.selectedOriginId() : null;
        List<ResourceLocation> ids = new ArrayList<>();
        for (OriginListEntry row : presenter.filteredRows()) {
            if (!row.isSectionHeader()) ids.add(row.id());
        }
        browseIds = ids;
        if (browseIds.isEmpty()) {
            browseIndex = 0;
            presenter.select(null);
            detail.clear();
            if (confirmButton != null) confirmButton.active = false;
            return;
        }
        int idx = prev == null ? -1 : browseIds.indexOf(prev);
        browseIndex = idx < 0 ? 0 : idx;
        syncSelection();
    }

    /** Point the presenter and the detail panel at the currently-browsed origin. */
    private void syncSelection() {
        ResourceLocation id = browseIds.get(browseIndex);
        presenter.select(id);
        var layer = presenter.currentLayer();
        detail.setOrigin(OriginDetailViewModel.compute(id,
            PickerCloseBehaviour.CLASS_LAYER_ID.equals(layer != null ? layer.id() : null)));
        if (confirmButton != null) confirmButton.active = true;
    }

    /** Step the browsed origin (wraps around). */
    private void browse(int delta) {
        int n = browseIds.size();
        if (n <= 1) return;
        browseIndex = Math.floorMod(browseIndex + delta, n);
        syncSelection();
    }

    private void advanceLayer() {
        presenter.buildRows();
        rebuildBrowseIds(false);
        refreshWidgets();
    }

    private void confirmSelection() {
        if (presenter.selectedOriginId() == null) return;
        boolean keepGoing = presenter.confirm();
        if (!keepGoing) { onClose(); return; }
        advanceLayer();
    }

    private void refreshWidgets() {
        clearWidgets();

        var modes = OriginSelectionPresenter.SortMode.values();
        addRenderableWidget(ParchmentButton.parchment(sortModeLabel(presenter.sortMode()), b -> {
            var nextMode = modes[(presenter.sortMode().ordinal() + 1) % modes.length];
            lastSortMode = nextMode;
            presenter.setSortMode(nextMode);
            presenter.buildRows();
            rebuildBrowseIds(false);
            refreshWidgets();
        }).bounds(width - 10 - 110, 8, 110, 22).shortStyle(true).build());

        var search = new ParchmentEditBox(font, 10, 11, 110, SEARCH_H,
            UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.label")));
        search.setMaxLength(64);
        search.setHint(UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.hint")));
        search.setTextColor(UITheme.current().descriptionColor());
        search.setValue(presenter.searchText());
        // No refreshWidgets() here: rebuilding the box mid-keystroke would drop focus.
        search.setResponder(text -> { if (presenter.setSearch(text)) rebuildBrowseIds(true); });
        addRenderableWidget(search);

        if (browseIds.size() > 1) {
            int navY = PANEL_TOP + DETAIL_PAD + 8;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> browse(-1))
                .bounds(panelX + PANEL_INSET, navY, NAV_W, NAV_H).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> browse(1))
                .bounds(panelX + panelW - PANEL_INSET - NAV_W, navY, NAV_W, NAV_H).build());
        }

        var layer = presenter.currentLayer();
        int cy = height - 32;
        int cx = width / 2;

        var randomBtn = ParchmentButton.parchment(Component.translatable("button.neoorigins.random"), b -> {
            ResourceLocation id = presenter.randomId();
            int i = id == null ? -1 : browseIds.indexOf(id);
            // randomId() draws from the unfiltered set; if search hid the roll,
            // roll again within what is actually on show.
            if (i < 0 && !browseIds.isEmpty()) i = (int) (Math.random() * browseIds.size());
            if (i >= 0) { browseIndex = i; syncSelection(); }
        }).bounds(panelX, cy, 70, 28).build();
        randomBtn.visible = layer.allowRandom();
        addRenderableWidget(randomBtn);

        var backBtn = ParchmentButton.parchment(Component.translatable("gui.neoorigins.button.back"), b -> {
            if (presenter.back()) advanceLayer();
        }).bounds(cx - 92, cy, 80, 28).build();
        backBtn.active = presenter.currentLayerIndex() > 0;
        addRenderableWidget(backBtn);

        confirmButton = ParchmentButton.parchment(Component.translatable("gui.neoorigins.button.confirm"),
                b -> confirmSelection())
            .bounds(cx + 12, cy, 80, 28).build();
        confirmButton.active = presenter.selectedOriginId() != null;
        addRenderableWidget(confirmButton);
    }

    /** Translation-key label for a sort mode, wrapped in the active theme font. */
    private Component sortModeLabel(OriginSelectionPresenter.SortMode mode) {
        String key = switch (mode) {
            case MANUAL     -> "gui.neoorigins.sort.manual";
            case NAME_ASC   -> "gui.neoorigins.sort.name_asc";
            case NAME_DESC  -> "gui.neoorigins.sort.name_desc";
            case CLASS      -> "gui.neoorigins.sort.class";
            case IMPACT_ASC -> "gui.neoorigins.sort.impact";
        };
        return UIThemeUtils.themed(Component.translatable(key));
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // No-op: we draw our own scrim in render(). Suppressed here rather than
        // in renderBlurredBackground because super.render() runs last on this
        // screen, so the vanilla darkening would land on top of the panel.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        g.fill(0, 0, width, height, theme.overlayColor());
        if (presenter.isDone()) return;

        // Title + layer progress sit on the dark scrim outside the parchment, so
        // they use a cream tone rather than the panel's dark brown header colour.
        final int SCRIM_TEXT = 0xFFEBD9B0;
        var layerTitle = UIThemeUtils.themedBold(
            Component.translatable("screen.neoorigins.choose_prompt", presenter.currentLayer().name()));
        g.drawString(font, layerTitle, width / 2 - font.width(layerTitle) / 2, 14, SCRIM_TEXT, false);
        var progComp = UIThemeUtils.themed(Component.literal(
            (presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers()));
        g.drawString(font, progComp, width - 10 - font.width(progComp), 32, SCRIM_TEXT, false);

        detail.renderHeader(g);

        // Browse position, in the header gutter between the impact row and the
        // scroll area — same slot the info screen uses.
        if (browseIds.size() > 1) {
            var posC = UIThemeUtils.themed(Component.translatable("gui.neoorigins.picker.position",
                String.valueOf(browseIndex + 1), String.valueOf(browseIds.size())));
            g.drawString(font, posC, panelX + panelW / 2 - font.width(posC) / 2,
                PANEL_TOP + detail.headerHeight() - 9, theme.mutedColor(), false);
        }

        detail.renderBody(g);

        // super LAST: the < / > arrows sit inside the panel's header rect and
        // would otherwise be painted over by it. (The two-panel screen has the
        // opposite order because none of its widgets overlap the detail panel.)
        super.render(g, mouseX, mouseY, partial);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrows page the carousel unless the search box has focus, where they
        // belong to the caret.
        if (!(getFocused() instanceof EditBox)) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { browse(-1); return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { browse(1);  return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (detail.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (detail.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (detail.mouseDragged(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (detail.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return PickerCloseBehaviour.shouldCloseOnEsc(isOrb, presenter); }

    @Override
    public void onClose() { PickerCloseBehaviour.onPickerClosed(isOrb, presenter); }
}
