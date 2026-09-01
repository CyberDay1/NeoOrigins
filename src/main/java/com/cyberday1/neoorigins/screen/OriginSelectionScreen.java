package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.theme.PanelRenderer;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.client.theme.UIThemeUtils;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.detail.OriginDetailPanel;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class OriginSelectionScreen extends Screen implements PickerScreen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_TOP        = 44;
    private static final int PANEL_BTM_MARGIN = 32;
    private static final int SEARCH_H         = 16;
    private static final int SEARCH_GAP       = 3;
    private static final int LIST_BTN_H       = 28;
    /** Negative on purpose: the scroll texture's art occupies only the middle
     *  ~13 of its 25px, leaving transparent top/bottom margins that stretch to
     *  ~6–7px at this button height. Overlapping the rows by that much packs the
     *  *opaque* scrolls to a tight ~4px visual gap without ever overlapping the
     *  art itself (the overlap lands entirely in the transparent margins). */
    private static final int LIST_BTN_GAP     = -9;
    private static final int MIN_LEFT_W       = 120;
    private static final int MAX_LEFT_W       = 160;
    private static final int PANEL_GAP        = 8;
    /** Inset for widgets inside the parchment panel — must clear the
     *  9-slice burnt-edge band (12px corners in {@link UITheme#PARCHMENT})
     *  plus a few pixels of breathing room so the curl decoration stays
     *  uninterrupted. */
    private static final int PANEL_INSET      = 16;
    /** Vertical padding from parchment top to the search box — same 12-px
     *  burnt-edge clearance as {@link #PANEL_INSET}. */
    private static final int SEARCH_TOP_PAD   = 16;

    private final boolean isOrb;
    private final boolean forceReselect;
    private final java.util.List<net.minecraft.resources.Identifier> scopedLayers;
    private final OriginSelectionPresenter presenter = new OriginSelectionPresenter();

    /**
     * Sort choice survives screen close → reopen within the same session.
     * Starts null and is seeded from the {@code default_sort} client config on
     * first open (config isn't reliably loaded at class-init time); a cycled
     * choice then overrides it for the rest of the session.
     */
    private static OriginSelectionPresenter.SortMode lastSortMode = null;

    // Computed layout geometry
    private int panelX, panelBottom, leftW, rightX, rightW, listTop, listVisibleCount;

    /** Shared right-hand detail view. Built in init() — Screen.font is null
     *  until then. Static evolution path only; no live kill progress here. */
    private OriginDetailPanel detail;

    // Widgets
    private final List<OriginButton> originButtons  = new ArrayList<>();
    private record VisibleHeader(int y, String label) {}
    private final List<VisibleHeader> visibleHeaders = new ArrayList<>();
    private Button confirmButton;

    public OriginSelectionScreen(boolean isOrb) {
        this(isOrb, false, java.util.List.of());
    }

    public OriginSelectionScreen(boolean isOrb, boolean forceReselect) {
        this(isOrb, forceReselect, java.util.List.of());
    }

    public OriginSelectionScreen(boolean isOrb, boolean forceReselect,
                                 java.util.List<net.minecraft.resources.Identifier> scopedLayers) {
        super(Component.translatable("screen.neoorigins.choose_origin"));
        this.isOrb = isOrb;
        this.forceReselect = forceReselect;
        this.scopedLayers = scopedLayers == null ? java.util.List.of() : java.util.List.copyOf(scopedLayers);
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
        // Screen.font is only populated by the time init() runs, so the panel
        // cannot be a field initialiser.
        detail = new OriginDetailPanel(font);
        boolean hasPending = presenter.init();
        if (!hasPending) { onClose(); return; }
        int totalW       = Math.max(280, width - 40);
        leftW            = Mth.clamp((int)(totalW * 0.24f), MIN_LEFT_W, MAX_LEFT_W);
        panelX           = (width - totalW) / 2;
        panelBottom      = height - PANEL_BTM_MARGIN;
        rightX           = panelX + leftW + PANEL_GAP;
        rightW           = totalW - leftW - PANEL_GAP;
        detail.setBounds(rightX, PANEL_TOP, rightW, panelBottom - PANEL_TOP);
        listTop          = PANEL_TOP + SEARCH_TOP_PAD + SEARCH_H + SEARCH_GAP;
        // Clamp the visible row count to the parchment *interior* — clearing the
        // bottom burnt-edge curl AND reserving the last row's full height. Using
        // the raw panelBottom here let the bottom rows spill past the scroll onto
        // the curl at low GUI scale / high resolution.
        int listBottomLimit = panelBottom - PANEL_INSET;
        int listRowStep     = LIST_BTN_H + listRowGap();
        listVisibleCount = Math.max(1, (listBottomLimit - listTop - LIST_BTN_H) / listRowStep + 1);
        presenter.buildRows();
        refreshWidgets();
        updateDetail();
    }

    private void advanceLayer() {
        presenter.buildRows();
        refreshWidgets();
        updateDetail();
    }

    private void selectOrigin(Identifier id) {
        presenter.select(id);
        originButtons.forEach(b -> b.setSelected(b.getOrigin().id().equals(id)));
        if (confirmButton != null) confirmButton.active = true;
        updateDetail();
    }

    private void confirmSelection() {
        if (presenter.selectedOriginId() == null) return;
        boolean keepGoing = presenter.confirm();
        if (!keepGoing) { onClose(); return; }
        advanceLayer();
    }

    private void updateDetail() {
        detail.setOrigin(OriginDetailViewModel.compute(presenter.selectedOriginId(),
            CLASS_LAYER_ID.equals(presenter.currentLayer() != null ? presenter.currentLayer().id() : null)));
    }

    private void refreshWidgets() {
        clearWidgets();
        originButtons.clear();
        visibleHeaders.clear();

        // Sort cycle — parchment-skinned. Displays the current sort label
        // and advances to the next mode on click. Width/position chosen so it
        // doesn't collide with the centred layer title or the right-aligned
        // "n / total" progress text.
        var modes = OriginSelectionPresenter.SortMode.values();
        var sortCycle = ParchmentButton.parchment(sortModeLabel(presenter.sortMode()), b -> {
            var current = presenter.sortMode();
            int next = (current.ordinal() + 1) % modes.length;
            var nextMode = modes[next];
            lastSortMode = nextMode;
            presenter.setSortMode(nextMode);
            presenter.buildRows();
            refreshWidgets();
        }).bounds(width - 10 - 110, 8, 110, 22).shortStyle(true).build();
        addRenderableWidget(sortCycle);

        var search = new ParchmentEditBox(font, panelX + PANEL_INSET, PANEL_TOP + SEARCH_TOP_PAD, leftW - 2 * PANEL_INSET, SEARCH_H,
            UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.label")));
        search.setMaxLength(64);
        search.setHint(UIThemeUtils.themed(Component.translatable("gui.neoorigins.search.hint")));
        search.setTextColor(UITheme.current().descriptionColor());
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
                    var btn = new OriginButton(panelX + PANEL_INSET, btnY, leftW - 2 * PANEL_INSET, LIST_BTN_H, origin,
                        b -> selectOrigin(rowId));
                    btn.setSelected(rowId.equals(presenter.selectedOriginId()));
                    originButtons.add(btn);
                    addRenderableWidget(btn);
                }
            }
            btnY += LIST_BTN_H + listRowGap();
        }

        var layer = presenter.currentLayer();
        int cy = height - 32;
        int cx = width / 2;

        var randomBtn = ParchmentButton.parchment(Component.translatable("button.neoorigins.random"), b -> {
            Identifier id = presenter.randomId();
            if (id != null) selectOrigin(id);
        }).bounds(panelX, cy, 70, 28).build();
        randomBtn.visible = layer.allowRandom();
        addRenderableWidget(randomBtn);

        var backBtn = ParchmentButton.parchment(Component.translatable("gui.neoorigins.button.back"), b -> {
            if (presenter.back()) advanceLayer();
        }).bounds(cx - 92, cy, 80, 28).build();
        backBtn.active = presenter.currentLayerIndex() > 0;
        addRenderableWidget(backBtn);

        confirmButton = ParchmentButton.parchment(Component.translatable("gui.neoorigins.button.confirm"), b -> confirmSelection())
            .bounds(cx + 12, cy, 80, 28).build();
        confirmButton.active = presenter.selectedOriginId() != null;
        addRenderableWidget(confirmButton);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // No-op: we draw our own background in render() to avoid the default
        // darkened overlay that would wash out our custom UI elements.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        // Full-screen scrim — kept dark behind the parchment panels.
        g.fill(0, 0, width, height, theme.overlayColor());
        if (presenter.isDone()) return;

        // Title + progress sit on the dark scrim *outside* the parchment, so
        // use a cream parchment-tone (not the dark brown headerColor used on
        // the panels) to keep them readable.
        final int SCRIM_TEXT = 0xFFEBD9B0;
        var layerTitle = UIThemeUtils.themedBold(Component.translatable("screen.neoorigins.choose_prompt", presenter.currentLayer().name()));
        // drawCenteredString forces a drop shadow; use drawString to keep text clean.
        g.text(font, layerTitle, width / 2 - font.width(layerTitle) / 2, 14, SCRIM_TEXT, false);
        Component progComp = UIThemeUtils.themed(Component.literal((presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers()));
        g.text(font, progComp, width - 10 - font.width(progComp), 32, SCRIM_TEXT, false);

        // Left list panel — parchment 9-slice.
        PanelRenderer.drawPanel(g, theme, panelX - 1, PANEL_TOP - 1, leftW + 2, panelBottom - PANEL_TOP + 2);

        for (var vh : visibleHeaders) {
            // Accent bar on the left edge of section headers.
            g.fill(panelX + PANEL_INSET, vh.y() + 5, panelX + PANEL_INSET + 2, vh.y() + LIST_BTN_H - 5, theme.accentColor());
            g.text(font, UIThemeUtils.themed(Component.literal(vh.label().toUpperCase())), panelX + PANEL_INSET + 6, vh.y() + 7, theme.headerColor(), false);
        }
        // Scroll hint sits above the list panel so it doesn't collide with the
        // Random / Back / Confirm button row at the bottom.
        if (getMaxListScroll() > 0) {
            var hint = UIThemeUtils.themed(Component.translatable("gui.neoorigins.hint.scroll"));
            int hintY = PANEL_TOP - 10;
            // Same cream parchment-tone as the title — readable on the scrim.
            g.text(font, hint, panelX, hintY, 0xFFEBD9B0, false);
        }

        super.extractRenderState(g, mouseX, mouseY, partial);
        detail.render(g);
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

    // ── Scrolling ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= panelX && mx <= panelX + leftW && my >= listTop && my <= panelBottom) {
            int next = Mth.clamp(presenter.listScrollOffset() + (sy > 0 ? -1 : 1), 0, getMaxListScroll());
            if (next != presenter.listScrollOffset()) { presenter.setListScrollOffset(next); refreshWidgets(); }
            return true;
        }
        if (detail.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // The detail panel gets first refusal on drags so its scroll thumb wins over
    // any widget under the cursor.
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

    private int getMaxListScroll() {
        return Math.max(0, presenter.filteredRows().size() - listVisibleCount);
    }

    /**
     * Vertical gap between consecutive list rows. The parchment scroll art has
     * transparent top/bottom margins, so rows are overlapped ({@link #LIST_BTN_GAP}
     * is negative) to pack the opaque scrolls to a tight ~4px visual gap. The flat
     * skin paints solid rectangles with no transparent margin, so a negative step
     * would make the rows literally overlap into one block — it needs a real
     * positive gap instead.
     */
    private static int listRowGap() {
        return UITheme.current().flat() ? 4 : LIST_BTN_GAP;
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final Identifier CLASS_LAYER_ID = PickerCloseBehaviour.CLASS_LAYER_ID;

    @Override
    public boolean shouldCloseOnEsc() { return PickerCloseBehaviour.shouldCloseOnEsc(isOrb, presenter); }

    @Override
    public void onClose() { PickerCloseBehaviour.onPickerClosed(isOrb, presenter); }
}
