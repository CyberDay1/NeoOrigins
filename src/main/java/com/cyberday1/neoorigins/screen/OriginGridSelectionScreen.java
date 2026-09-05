package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.detail.OriginDetailPanel;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A paged wall of origin cards — the layout selected by
 * {@code ui.picker_layout = GRID}. Two views share one screen: the grid itself,
 * and the full {@link OriginDetailPanel} a card opens onto.
 *
 * <p>The split exists because a card is too small to carry a description. Rather
 * than shrink the grid to make room for a permanent detail column (which would
 * cost roughly half the cards per page), a click promotes the origin to a
 * full-width read and Back returns. Random / Back / Confirm stay in the same
 * three places across both views so the commit controls never move under the
 * cursor; Back relabels itself to say which of the two things it will do.
 *
 * <p>Clicking a card also selects it, matching the carousel's "browsing is
 * selecting" rule: Confirm always applies to the last card touched, whichever
 * view is showing.
 */
public class OriginGridSelectionScreen extends Screen implements PickerScreen {

    private static final int GRID_TOP     = 44;
    private static final int GRID_MARGIN  = 16;
    private static final int CARD_W       = 80;
    /** Card content floor is 65px (icon block + two name lines); the dots need 12 more. */
    private static final int CARD_H       = 78;
    /** Compact fallback for short windows — one name line and a 24px icon. */
    private static final int CARD_MIN_W   = 64;
    private static final int CARD_MIN_H   = 54;
    private static final int CARD_GAP     = 6;
    private static final int PAGE_ROW_H   = 20;
    private static final int PAGE_ARROW_W = 20;
    private static final int BOTTOM_BAR_Y = 32;
    private static final int SEARCH_H     = 16;

    /** Detail-view panel geometry, matching the carousel so the two read alike. */
    private static final int PANEL_BTM_MARGIN = 32;

    private final boolean isOrb;
    private final boolean forceReselect;
    private final List<Identifier> scopedLayers;
    private final OriginSelectionPresenter presenter = new OriginSelectionPresenter();

    /** Sort choice survives close → reopen within a session, as on the other layouts. */
    private static OriginSelectionPresenter.SortMode lastSortMode = null;

    /** Built in init() — Screen.font is null until then. */
    private OriginDetailPanel detail;
    private Button confirmButton;
    private Button prevPageBtn, nextPageBtn;

    /**
     * The cards, flattened from {@link OriginSelectionPresenter#filteredRows()}
     * with section headers dropped. Deliberately not {@code allOriginIds()}: the
     * search filter only touches the filtered rows, so paging the raw id list
     * would ignore whatever the player typed.
     */
    private List<Identifier> browseIds = List.of();
    private int page = 0;
    private int cols = 1, rows = 1, gridX = 0, gridBottom = 0;
    /** Live card size, chosen per window by {@link #layoutGrid()}. */
    private int cardW = CARD_W, cardH = CARD_H;

    /** False = the card wall, true = the promoted detail read. */
    private boolean detailView = false;

    public OriginGridSelectionScreen(boolean isOrb, boolean forceReselect,
                                     List<Identifier> scopedLayers) {
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

        layoutGrid();
        layoutDetailPanel();

        presenter.buildRows();
        rebuildBrowseIds(false);
        refreshWidgets();
    }

    /**
     * Fit as many whole cards as the window allows, then centre the block. The
     * page row's height is reserved unconditionally, even on a single page:
     * giving that space back would be circular, since the page count depends on
     * the per-page capacity this method is working out.
     */
    private void layoutGrid() {
        gridBottom = height - BOTTOM_BAR_Y - CARD_GAP - PAGE_ROW_H - CARD_GAP;
        int availW = width - GRID_MARGIN * 2;
        int availH = gridBottom - GRID_TOP;

        cardW = CARD_W;
        cardH = CARD_H;
        cols = Math.max(1, (availW + CARD_GAP) / (cardW + CARD_GAP));
        rows = Math.max(1, (availH + CARD_GAP) / (cardH + CARD_GAP));

        // Minecraft's auto GUI scale lands the scaled height at 240-270 on most
        // setups, and 108px of that is chrome, so a preferred card leaves room
        // for exactly one row. A wall of three is not a wall: shrink the card
        // rather than page the player through the layer three origins at a time.
        if (rows < 2) {
            int compactRows = (availH + CARD_GAP) / (CARD_MIN_H + CARD_GAP);
            if (compactRows > rows) {
                cardW = CARD_MIN_W;
                cardH = CARD_MIN_H;
                cols = Math.max(1, (availW + CARD_GAP) / (cardW + CARD_GAP));
                rows = compactRows;
            }
        }

        int blockW = cols * cardW + (cols - 1) * CARD_GAP;
        gridX = (width - blockW) / 2;
    }

    private void layoutDetailPanel() {
        int panelW = Math.min(width - 40, 400);
        int panelX = (width - panelW) / 2;
        int panelBottom = height - PANEL_BTM_MARGIN;
        detail.setBounds(panelX, GRID_TOP, panelW, panelBottom - GRID_TOP);
    }

    private int perPage()   { return cols * rows; }
    private int pageCount() { return Math.max(1, (browseIds.size() + perPage() - 1) / perPage()); }

    private void rebuildBrowseIds(boolean keepSelection) {
        Identifier prev = keepSelection ? presenter.selectedOriginId() : null;
        List<Identifier> ids = new ArrayList<>();
        for (OriginListEntry row : presenter.filteredRows()) {
            if (!row.isSectionHeader()) ids.add(row.id());
        }
        browseIds = ids;
        if (!keepSelection) {
            presenter.select(null);
            detail.clear();
            detailView = false;
        }
        // Follow a kept selection to whichever page it landed on; otherwise the
        // highlighted card can end up off-page after a sort or search change.
        int idx = prev == null ? -1 : browseIds.indexOf(prev);
        if (idx >= 0) {
            page = idx / perPage();
        } else {
            page = 0;
            if (prev != null) {
                // The search hid the selection. Drop it rather than leave Confirm
                // armed on a card the player can no longer see.
                presenter.select(null);
                detail.clear();
                detailView = false;
            }
        }
        page = Math.min(page, pageCount() - 1);
        if (confirmButton != null) confirmButton.active = presenter.selectedOriginId() != null;
    }

    /** Point the presenter and the detail panel at one origin. */
    private void selectOrigin(Identifier id) {
        presenter.select(id);
        var layer = presenter.currentLayer();
        detail.setOrigin(OriginDetailViewModel.compute(id,
            PickerCloseBehaviour.CLASS_LAYER_ID.equals(layer != null ? layer.id() : null)));
        if (confirmButton != null) confirmButton.active = true;
    }

    private void turnPage(int delta) {
        int n = pageCount();
        if (n <= 1) return;
        page = Math.floorMod(page + delta, n);
        refreshWidgets();
    }

    private void advanceLayer() {
        // No layoutGrid() here: the window has not changed size, and a resize
        // re-enters init() anyway.
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

    // ── Widgets ───────────────────────────────────────────────────────────────

    private void refreshWidgets() {
        clearWidgets();
        // Dropped with the widgets they point at; buildGridWidgets() re-seats them.
        prevPageBtn = null;
        nextPageBtn = null;
        if (!detailView) buildGridWidgets();
        buildCommitBar();
    }

    private void buildGridWidgets() {
        var modes = OriginSelectionPresenter.SortMode.values();
        addRenderableWidget(ParchmentButton.parchment(sortModeLabel(presenter.sortMode()), b -> {
            var nextMode = modes[(presenter.sortMode().ordinal() + 1) % modes.length];
            lastSortMode = nextMode;
            presenter.setSortMode(nextMode);
            presenter.buildRows();
            rebuildBrowseIds(true);
            refreshWidgets();
        }).bounds(width - 10 - 110, 8, 110, 22).shortStyle(true).build());

        var search = new ParchmentEditBox(font, 10, 11, 110, SEARCH_H,
            UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.label")));
        search.setMaxLength(64);
        search.setHint(UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.hint")));
        search.setTextColor(UITheme.current().descriptionColor());
        search.setValue(presenter.searchText());
        // Rebuilding the cards in place, not through refreshWidgets(): a full
        // rebuild mid-keystroke would drop the box's focus.
        search.setResponder(text -> {
            if (presenter.setSearch(text)) {
                rebuildBrowseIds(true);
                rebuildCards();
                updatePageControls();
            }
        });
        addRenderableWidget(search);

        addCards();

        // Always built, visibility toggled: a search that shrinks the results to
        // one page has to be able to hide these without a full widget rebuild,
        // which would steal focus from the box being typed into.
        int py = gridBottom + CARD_GAP;
        int cx = width / 2;
        prevPageBtn = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
            .bounds(cx - 50, py, PAGE_ARROW_W, PAGE_ROW_H).build());
        nextPageBtn = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
            .bounds(cx + 30, py, PAGE_ARROW_W, PAGE_ROW_H).build());
        updatePageControls();
    }

    private void updatePageControls() {
        boolean paged = pageCount() > 1;
        if (prevPageBtn != null) prevPageBtn.visible = paged;
        if (nextPageBtn != null) nextPageBtn.visible = paged;
    }

    /**
     * Swap only the cards, leaving every other widget (and the focused search
     * box) untouched. Cards are the trailing block added by {@link #addCards()},
     * so they can be dropped by identity without disturbing widget order.
     */
    private void rebuildCards() {
        List<OriginCardButton> stale = new ArrayList<>();
        for (var child : children()) {
            if (child instanceof OriginCardButton card) stale.add(card);
        }
        for (OriginCardButton card : stale) {
            removeWidget(card);
        }
        addCards();
    }

    private void addCards() {
        int start = page * perPage();
        int end = Math.min(browseIds.size(), start + perPage());
        Identifier selected = presenter.selectedOriginId();
        for (int i = start; i < end; i++) {
            Identifier id = browseIds.get(i);
            Origin origin = OriginDataManager.INSTANCE.getOrigin(id);
            // buildRows() already dropped unknown ids; this guards a datapack
            // reload landing between that call and this frame.
            if (origin == null) continue;
            int slot = i - start;
            int cx = gridX + (slot % cols) * (cardW + CARD_GAP);
            int cy = GRID_TOP + (slot / cols) * (cardH + CARD_GAP);
            var card = new OriginCardButton(cx, cy, cardW, cardH, origin, b -> {
                selectOrigin(id);
                detailView = true;
                refreshWidgets();
            });
            card.setSelected(id.equals(selected));
            addRenderableWidget(card);
        }
    }

    /**
     * Random / Back / Confirm, in the same three places on both views.
     *
     * <p>Back is the one control that changes meaning: from a card's detail it
     * returns to the wall, and only from the wall does it step back a layer. A
     * separate top-corner "back to grid" button was the first attempt and it was
     * wrong — players reach for the Back they can already see, and finding it
     * greyed out (it disables itself on the first layer) reads as a dead screen.
     */
    private void buildCommitBar() {
        var layer = presenter.currentLayer();
        int cy = height - BOTTOM_BAR_Y;
        int cx = width / 2;

        var randomBtn = ParchmentButton.parchment(Component.translatable("button.neoorigins.random"), b -> {
            Identifier id = presenter.randomId();
            // randomId() draws from the unfiltered set; if search hid the roll,
            // roll again within what is actually on show.
            if ((id == null || !browseIds.contains(id)) && !browseIds.isEmpty()) {
                id = browseIds.get((int) (Math.random() * browseIds.size()));
            }
            if (id == null) return;
            selectOrigin(id);
            page = browseIds.indexOf(id) / perPage();
            refreshWidgets();
        }).bounds(GRID_MARGIN, cy, 70, 28).build();
        randomBtn.visible = layer.allowRandom();
        addRenderableWidget(randomBtn);

        // Label says where it goes, so the change of meaning is visible rather
        // than something the player has to discover by pressing it.
        var backLabel = detailView
            ? Component.translatable("gui.neoorigins.picker.back_to_grid")
            : Component.translatable("gui.neoorigins.button.back");
        var backBtn = ParchmentButton.parchment(backLabel, b -> {
            if (detailView) {
                detailView = false;
                refreshWidgets();
            } else if (presenter.back()) {
                advanceLayer();
            }
        }).bounds(cx - 92, cy, 80, 28).build();
        backBtn.active = detailView || presenter.currentLayerIndex() > 0;
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

    // 26.x: no blur behind our own scrim.
    @Override protected void extractBlurredBackground(GuiGraphicsExtractor g) { /* no blur */ }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        g.fill(0, 0, width, height, theme.overlayColor());
        if (presenter.isDone()) return;

        // Title + layer progress sit on the dark scrim outside the parchment, so
        // they use a cream tone rather than the panel's dark brown header colour.
        final int SCRIM_TEXT = 0xFFEBD9B0;
        var layerTitle = UIThemeUtils.themedBold(
            Component.translatable("screen.neoorigins.choose_prompt", presenter.currentLayer().name()));
        g.text(font, layerTitle, width / 2 - font.width(layerTitle) / 2, 14, SCRIM_TEXT, false);
        var progComp = UIThemeUtils.themed(Component.literal(
            (presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers()));
        g.text(font, progComp, width - 10 - font.width(progComp), 32, SCRIM_TEXT, false);

        if (detailView) {
            detail.renderHeader(g);
            detail.renderBody(g);
        } else {
            renderGridChrome(g, theme, SCRIM_TEXT);
        }

        // super LAST: the cards and the detail panel are both drawn by this
        // method, and the page arrows sit in the gutter directly beneath them.
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void renderGridChrome(GuiGraphicsExtractor g, UITheme theme, int scrimText) {
        if (browseIds.isEmpty()) {
            var empty = UIThemeUtils.themed(Component.translatable("gui.neoorigins.picker.no_results"));
            g.text(font, empty, width / 2 - font.width(empty) / 2,
                GRID_TOP + 20, theme.mutedColor(), false);
            return;
        }
        if (pageCount() > 1) {
            var posC = UIThemeUtils.themed(Component.translatable("gui.neoorigins.picker.position",
                String.valueOf(page + 1), String.valueOf(pageCount())));
            g.text(font, posC, width / 2 - font.width(posC) / 2,
                gridBottom + CARD_GAP + (PAGE_ROW_H - 8) / 2, scrimText, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // ESC out of the detail read returns to the grid instead of leaving the
        // picker. The close lock in PickerCloseBehaviour still governs the grid.
        if (detailView && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            detailView = false;
            refreshWidgets();
            return true;
        }
        // Arrows page the grid unless the search box has focus, where they
        // belong to the caret.
        if (!detailView && !(getFocused() instanceof EditBox)) {
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { turnPage(-1); return true; }
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { turnPage(1);  return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (detailView) {
            if (detail.mouseScrolled(mx, my, sy)) return true;
        } else if (my >= GRID_TOP && my <= gridBottom && pageCount() > 1) {
            turnPage(sy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromWidget) {
        if (detailView && detail.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, fromWidget);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (detailView && detail.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (detailView && detail.mouseReleased(event.x(), event.y(), event.button())) return true;
        return super.mouseReleased(event);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return PickerCloseBehaviour.shouldCloseOnEsc(isOrb, presenter); }

    @Override
    public void onClose() { PickerCloseBehaviour.onPickerClosed(isOrb, presenter); }
}
