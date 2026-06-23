package com.cyberday1.neoorigins.screen;

import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.client.theme.PanelRenderer;
import com.cyberday1.neoorigins.client.theme.UITheme;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.screen.model.OriginDetailViewModel;
import com.cyberday1.neoorigins.screen.model.OriginListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class OriginSelectionScreen extends Screen {

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
    private static final int DETAIL_PAD       = 10;
    /** Inset for widgets inside the parchment panel — must clear the
     *  9-slice burnt-edge band (12px corners in {@link UITheme#PARCHMENT})
     *  plus a few pixels of breathing room so the curl decoration stays
     *  uninterrupted. */
    private static final int PANEL_INSET      = 16;
    /** Vertical padding from parchment top to the search box — same 12-px
     *  burnt-edge clearance as {@link #PANEL_INSET}. */
    private static final int SEARCH_TOP_PAD   = 16;
    private static final int HEADER_H         = DETAIL_PAD + 32 + 6 + 9 + 4 + 5 + 10; // 76
    private static final int DOT_SIZE         = 5;
    private static final int DOT_SPACING      = 8;
    private static final int DOT_COUNT        = 4;
    private static final int LINE_H           = 10;
    /** Vertical gap between consecutive power entries in the detail panel. */
    private static final int POWER_GAP        = 5;

    private final boolean isOrb;
    private final boolean forceReselect;
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
        if (lastSortMode == null) {
            lastSortMode = com.cyberday1.neoorigins.client.NeoOriginsClientConfig.defaultSortMode();
        }
        presenter.setSortMode(lastSortMode);
        if (!presenter.init()) { onClose(); return; }
        int totalW       = Math.max(280, width - 40);
        leftW            = Mth.clamp((int)(totalW * 0.24f), MIN_LEFT_W, MAX_LEFT_W);
        panelX           = (width - totalW) / 2;
        panelBottom      = height - PANEL_BTM_MARGIN;
        rightX           = panelX + leftW + PANEL_GAP;
        rightW           = totalW - leftW - PANEL_GAP;
        detailTextW      = rightW - DETAIL_PAD * 2 - 6;
        listTop          = PANEL_TOP + SEARCH_TOP_PAD + SEARCH_H + SEARCH_GAP;
        // Clamp the visible row count to the parchment *interior* — clearing the
        // bottom burnt-edge curl (the same PANEL_INSET the detail panel respects
        // for its scroll area) AND reserving the last row's full height. Using the
        // raw panelBottom here let the bottom rows spill past the scroll onto the
        // curl at low GUI scale / high resolution.
        int listBottomLimit = panelBottom - PANEL_INSET;
        int listRowStep     = LIST_BTN_H + listRowGap();
        listVisibleCount = Math.max(1, (listBottomLimit - listTop - LIST_BTN_H) / listRowStep + 1);
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

    private void selectOrigin(ResourceLocation id) {
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
        detailViewModel = OriginDetailViewModel.compute(presenter.selectedOriginId(),
            CLASS_LAYER_ID.equals(presenter.currentLayer() != null ? presenter.currentLayer().id() : null));
        if (detailViewModel.origin() != null) {
            // Wrap with themed() BEFORE splitting — Font.split bakes the style
            // (including font selector) into each FormattedCharSequence, so the
            // themed font has to be on the Component before the split runs.
            descLines = font.split(themed(detailViewModel.origin().description()), detailTextW);

            // Pre-wrap power descriptions
            List<String> pDescs = detailViewModel.powerDescs();
            List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
            int powerDescW = detailTextW - 8; // indent for bullet point
            for (String desc : pDescs) {
                if (desc.isEmpty()) {
                    wrapped.add(List.of());
                } else {
                    wrapped.add(font.split(themed(Component.literal(desc)), powerDescW));
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
        if (detailViewModel.origin() != null && detailViewModel.origin().spawnLocation().isPresent()
            && !detailViewModel.origin().spawnLocation().get().formatSummary().isEmpty()) {
            h += LINE_H;
        }
        List<String> pNames = detailViewModel.powerNames();
        if (!pNames.isEmpty()) {
            h += 9 + 4; // "Powers" header
            for (int i = 0; i < pNames.size(); i++) {
                h += 11; // power name line
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    h += wrappedPowerDescs.get(i).size() * LINE_H;
                }
                h += POWER_GAP;
            }
        }
        return h + 6; // bottom padding
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
            themed(Component.translatable("gui.neoorigins.search.label")));
        search.setMaxLength(64);
        search.setHint(themed(Component.translatable("gui.neoorigins.search.hint")));
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
                    final ResourceLocation rowId = row.id();
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
            ResourceLocation id = presenter.randomId();
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // No-op: we draw our own background in render() to avoid the default
        // darkened overlay that would wash out our custom UI elements.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        UITheme theme = UITheme.current();
        // Full-screen scrim — kept dark behind the parchment panels.
        g.fill(0, 0, width, height, theme.overlayColor());
        if (presenter.isDone()) return;

        // Title + progress sit on the dark scrim *outside* the parchment, so
        // use a cream parchment-tone (not the dark brown headerColor used on
        // the panels) to keep them readable.
        final int SCRIM_TEXT = 0xFFEBD9B0;
        var layerTitle = themedBold(Component.translatable("screen.neoorigins.choose_prompt", presenter.currentLayer().name()));
        // drawCenteredString forces a drop shadow; use drawString to keep text clean.
        g.drawString(font, layerTitle, width / 2 - font.width(layerTitle) / 2, 14, SCRIM_TEXT, false);
        Component progComp = themed(Component.literal((presenter.currentLayerIndex() + 1) + " / " + presenter.totalLayers()));
        g.drawString(font, progComp, width - 10 - font.width(progComp), 32, SCRIM_TEXT, false);

        // Left list panel — parchment 9-slice.
        PanelRenderer.drawPanel(g, theme, panelX - 1, PANEL_TOP - 1, leftW + 2, panelBottom - PANEL_TOP + 2);

        for (var vh : visibleHeaders) {
            // Accent bar on the left edge of section headers.
            g.fill(panelX + PANEL_INSET, vh.y() + 5, panelX + PANEL_INSET + 2, vh.y() + LIST_BTN_H - 5, theme.accentColor());
            g.drawString(font, themed(Component.literal(vh.label().toUpperCase())), panelX + PANEL_INSET + 6, vh.y() + 7, theme.headerColor(), false);
        }
        // Scroll hint sits above the list panel so it doesn't collide with the
        // Random / Back / Confirm button row at the bottom.
        if (getMaxListScroll() > 0) {
            var hint = themed(Component.translatable("gui.neoorigins.hint.scroll"));
            int hintY = PANEL_TOP - 10;
            // Same cream parchment-tone as the title — readable on the scrim.
            g.drawString(font, hint, panelX, hintY, 0xFFEBD9B0, false);
        }

        super.render(g, mouseX, mouseY, partial);
        renderDetailPanel(g);
    }

    /**
     * Wraps a Component with the theme's font Style so a custom font provider
     * (e.g. a user-supplied TTF) can take effect. The renderer stays the
     * vanilla {@link net.minecraft.client.gui.Font}; the {@link ResourceLocation}
     * in the Style selects which font set is used.
     */
    private static Component themed(Component c) {
        ResourceLocation fid = UITheme.current().font();
        return fid != null ? c.copy().withStyle(s -> s.withFont(fid)) : c;
    }

    /** Like {@link #themed} but also marks the Style as bold — used for the
     *  detail-panel "Powers" section header and per-power name lines so the
     *  TTF renderer picks up its synthesized bold weight. */
    private static Component themedBold(Component c) {
        ResourceLocation fid = UITheme.current().font();
        return c.copy().withStyle(s -> {
            var styled = s.withBold(true);
            return fid != null ? styled.withFont(fid) : styled;
        });
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
        return themed(Component.translatable(key));
    }

    private void renderDetailPanel(GuiGraphics g) {
        UITheme theme = UITheme.current();
        // Right detail panel — parchment 9-slice.
        PanelRenderer.drawPanel(g, theme, rightX - 1, PANEL_TOP - 1, rightW + 2, panelBottom - PANEL_TOP + 2);

        if (detailViewModel.origin() == null) {
            var hint = themed(Component.translatable("gui.neoorigins.hint.select"));
            g.drawString(font, hint,
                rightX + rightW / 2 - font.width(hint) / 2,
                PANEL_TOP + (panelBottom - PANEL_TOP) / 2 - 4, theme.mutedColor(), false);
            return;
        }

        Origin origin = detailViewModel.origin();
        int cx = rightX + rightW / 2;
        int y  = PANEL_TOP + DETAIL_PAD;

        g.renderOutline(cx - 16, y, 32, 32, theme.borderColor());
        OriginButton.renderIcon(g, origin.icon(), cx - 8, y + 8);
        y += 32 + 6;
        var nameC = themedBold(origin.name());
        g.drawString(font, nameC, cx - font.width(nameC) / 2, y, theme.nameColor(), false);
        y += 9 + 4;
        drawImpactRow(g, cx, y, origin.impact());

        int scrollTop    = PANEL_TOP + HEADER_H;
        // Pull the bottom in so the rail clears the parchment burnt-edge curl.
        int scrollBottom = panelBottom - PANEL_INSET;
        int scrollAreaH  = scrollBottom - scrollTop;
        int maxScroll    = Math.max(0, detailContentH - scrollAreaH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxScroll);

        g.enableScissor(rightX + 1, scrollTop, rightX + rightW - 5, scrollBottom);
        int sy = scrollTop - detailScrollOffset;
        g.fill(rightX + DETAIL_PAD, sy + 3, rightX + rightW - DETAIL_PAD - 6, sy + 4, theme.borderColor());
        sy += 8;
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, rightX + DETAIL_PAD, sy, theme.descriptionColor(), false);
            sy += LINE_H;
        }
        if (detailViewModel.origin() != null && detailViewModel.origin().spawnLocation().isPresent()) {
            String spawnSummary = detailViewModel.origin().spawnLocation().get().formatSummary();
            if (!spawnSummary.isEmpty()) {
                g.drawString(font, themed(Component.literal(spawnSummary)),
                    rightX + DETAIL_PAD, sy, theme.accentColor(), false);
                sy += LINE_H;
            }
        }
        sy += 8;
        List<String> pNames = detailViewModel.powerNames();
        if (!pNames.isEmpty()) {
            g.drawString(font, themedBold(Component.translatable("gui.neoorigins.detail.powers_header")), rightX + DETAIL_PAD, sy, theme.headerColor(), false);
            sy += 9 + 4;
            for (int i = 0; i < pNames.size(); i++) {
                g.fill(rightX + DETAIL_PAD, sy + 3, rightX + DETAIL_PAD + 3, sy + 6, theme.accentColor());
                var pNameC = themedBold(Component.literal(pNames.get(i)));
                g.drawString(font, pNameC, rightX + DETAIL_PAD + 8, sy, theme.powerNameColor(), false);
                // Hotkey tag (e.g. "[R]") — the key the ability gets, same
                // slot logic as the HUD cluster. Accent color, after the name.
                List<String> pTags = detailViewModel.powerKeyTags();
                if (i < pTags.size() && !pTags.get(i).isEmpty()) {
                    g.drawString(font, themed(Component.literal(pTags.get(i))),
                        rightX + DETAIL_PAD + 8 + font.width(pNameC) + 5, sy, theme.accentColor(), false);
                }
                sy += 11;
                if (i < wrappedPowerDescs.size() && !wrappedPowerDescs.get(i).isEmpty()) {
                    for (FormattedCharSequence dLine : wrappedPowerDescs.get(i)) {
                        g.drawString(font, dLine, rightX + DETAIL_PAD + 8, sy, theme.powerDescriptionColor(), false);
                        sy += LINE_H;
                    }
                }
                sy += POWER_GAP;
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            // Sit the scroll rail inside the parchment burnt-edge curl
            // (PANEL_INSET = 12) so it doesn't run off the curled paper border.
            int barX   = rightX + rightW - PANEL_INSET;
            int thumbH = Math.max(10, scrollAreaH * scrollAreaH / (scrollAreaH + maxScroll));
            int thumbY = scrollTop + (int) ((long) detailScrollOffset * (scrollAreaH - thumbH) / maxScroll);
            g.fill(barX, scrollTop, barX + 1, scrollBottom, theme.borderColor());
            g.fill(barX, thumbY, barX + 1, thumbY + thumbH, theme.accentColor());
        }
    }

    private void drawImpactRow(GuiGraphics g, int cx, int y, Impact impact) {
        UITheme theme = UITheme.current();
        int totalW = (DOT_COUNT - 1) * DOT_SPACING + DOT_SIZE;
        int x0     = cx - totalW / 2;
        for (int i = 0; i < DOT_COUNT; i++)
            g.fill(x0 + i * DOT_SPACING, y, x0 + i * DOT_SPACING + DOT_SIZE, y + DOT_SIZE,
                i < impact.getDotCount() ? theme.accentColor() : theme.borderColor());
        Component label = Component.translatable("origins.gui.impact.impact").append(": ")
            .append(switch (impact) {
                case NONE   -> Component.translatable("origins.gui.impact.none");
                case LOW    -> Component.translatable("origins.gui.impact.low");
                case MEDIUM -> Component.translatable("origins.gui.impact.medium");
                case HIGH   -> Component.translatable("origins.gui.impact.high");
            });
        g.drawString(font, themed(label), cx + totalW / 2 + 6, y - 1, theme.mutedColor(), false);
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
            int scrollAreaH = (panelBottom - PANEL_INSET) - (PANEL_TOP + HEADER_H);
            int maxScroll   = Math.max(0, detailContentH - scrollAreaH);
            detailScrollOffset = Mth.clamp(detailScrollOffset + (sy > 0 ? -14 : 14), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
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

    /**
     * Lock players into the mandatory initial origin selection: ESC must not
     * dismiss the screen until they have actually chosen. This is the fix for
     * the divergence where the picker could be escaped on a dedicated server.
     *
     * <p>Escape stays allowed for the cases where backing out is intended:
     * <ul>
     *   <li>{@code isOrb} — an orb re-roll is voluntary and {@link #onClose()}
     *       refunds/cancels it.</li>
     *   <li>the player already has all origins (voluntary re-selection via the
     *       editor / {@code /origin} command / VIEW_INFO recovery once done).</li>
     *   <li>the local selection is already complete ({@code presenter.isDone()})
     *       — by then {@code confirm()} normally closes the screen anyway; this
     *       is just a safety net.</li>
     * </ul>
     */
    @Override
    public boolean shouldCloseOnEsc() {
        if (isOrb) return true;
        if (ClientOriginState.isHadAllOrigins()) return true;
        return presenter.isDone();
    }

    private static final ResourceLocation CLASS_LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class");
    private static final ResourceLocation NITWIT_ORIGIN_ID =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class_nitwit");

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
        // Only auto-assign nitwit when it's actually a selectable class. A pack
        // can disable every class origin (including class_nitwit) — in that case
        // the presenter skips the empty class layer entirely, and sending the
        // nitwit choice anyway makes the server reject it with a "non-existent
        // origin" warning. Mirror the presenter's availability filter so we stay
        // silent when there's nothing to assign.
        boolean nitwitAssigned = false;
        if (hasAnyOrigin && !hasClass && isNitwitAssignable()) {
            PacketDistributor.sendToServer(new ChooseOriginPayload(CLASS_LAYER_ID, NITWIT_ORIGIN_ID));
            nitwitAssigned = true;
        }
        // Tell the server to cancel any pending orb-of-origin commit. If the
        // player already picked at least once during this picker session, the
        // commit already fired and the server's flag is already cleared, so
        // this is a no-op. If they never picked, the orb stays in the
        // inventory and no XP is charged.
        if (isOrb) {
            PacketDistributor.sendToServer(new com.cyberday1.neoorigins.network.payload.CancelOrbPayload());
        }
        // Non-orb picker closed with an INCOMPLETE pick (zero origins OR some
        // but not all required layers): tell the server to drop first-pick
        // invulnerability. Now that the invuln gate covers the whole multi-layer
        // pick (not just the first layer), a player who picks an origin then
        // escapes before choosing a class would otherwise stay immortal until a
        // relog. The nitwit auto-assign above can complete the class layer; pass
        // that through so a legitimate completion isn't falsely reported as
        // abandoned (ClientOriginState won't reflect the just-sent packet yet).
        if (!isOrb && !isPickComplete(nitwitAssigned)) {
            PacketDistributor.sendToServer(new com.cyberday1.neoorigins.network.payload.PickerAbandonedPayload());
        }
        Minecraft.getInstance().setScreen(null);
    }

    /**
     * Client-side mirror of the server's {@code allFilled} loop in
     * {@code NeoOriginsNetwork.handleChooseOrigin}: true when every layer the
     * picker would actually show has a selection. Iterates the same sorted
     * layers, skips hidden layers and layers with no available+existing origin,
     * and checks {@link ClientOriginState#getOrigins()} for an entry per layer.
     *
     * @param nitwitAssigned true if onClose just auto-assigned the nitwit class
     *     this frame — its {@link ChooseOriginPayload} is in flight to the
     *     server but not yet reflected in {@code ClientOriginState}, so treat
     *     the class layer as filled to avoid a spurious abandon after a legit
     *     completion.
     */
    private boolean isPickComplete(boolean nitwitAssigned) {
        var choices = ClientOriginState.getOrigins();
        for (var l : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (l.hidden()) continue;
            boolean hasAnyOrigin = l.origins().stream()
                .anyMatch(co -> co.isAvailable(choices)
                             && OriginDataManager.INSTANCE.hasOrigin(co.origin()));
            if (!hasAnyOrigin) continue;
            boolean filled = choices.containsKey(l.id());
            if (!filled && nitwitAssigned && l.id().equals(CLASS_LAYER_ID)) filled = true;
            if (!filled) return false;
        }
        return true;
    }

    /**
     * True only when {@code neoorigins:class_nitwit} is a real, selectable
     * option right now — mirrors {@link OriginSelectionPresenter}'s availability
     * filter so onClose() doesn't auto-assign a class the server will reject.
     */
    private boolean isNitwitAssignable() {
        var layer = LayerDataManager.INSTANCE.getLayer(CLASS_LAYER_ID);
        if (layer == null || layer.hidden()) return false;
        var choices = ClientOriginState.getOrigins();
        for (var co : layer.origins()) {
            if (!co.origin().equals(NITWIT_ORIGIN_ID)) continue;
            if (!co.isAvailable(choices)) return false;
            if (ContentTogglesConfig.isOriginDisabled(co.origin())) return false;
            if (!OriginDataManager.INSTANCE.hasOrigin(co.origin())) return false;
            var origin = OriginDataManager.INSTANCE.getOrigin(co.origin());
            if (origin != null && origin.unchoosable()) return false;
            return true;
        }
        return false;
    }
}
