package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.ItemPickerOverlay;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.cyberday1.neoorigins.screen.creator.widget.ScrollPanel;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops tab — edits {@code DropRules} on the draft. Header section + a
 * scrollable list of drop rows; each row carries the union of fields
 * (item / count min,max / chance / rolls / weight) and the active
 * {@code strategy} on the parent decides which fields the serializer honours.
 *
 * <p>Dynamic rows are managed by a full tab rebuild ({@link MobOriginCreatorScreen#requestRebuild()})
 * on add/remove — same idiom Phase 3 used in the player creator's PowersTab.
 * Per-row item picking goes through the shared {@link ItemPickerOverlay}; the
 * row index being edited is stashed in {@link #editingItemRow}.
 */
public final class MobDropsTab implements MobCreatorTab {

    private static final int MAX_ROWS = 32;
    private static final List<String> YES_NO = List.of("no", "yes");
    private static final List<String> MODES = List.of("additive", "replace");
    private static final List<String> STRATEGIES = List.of("independent_chance", "weighted_pool");

    private static final int ROW_H = 22, BOX_H = 14, LIST_ROW_H = 18;
    private static final int Y_HEADER       = 0;
    private static final int Y_ENABLED      = 14;
    private static final int Y_MODE_STRAT   = Y_ENABLED     + ROW_H;
    private static final int Y_POOL_ROLLS   = Y_MODE_STRAT  + ROW_H;
    private static final int Y_LIST_HEADER  = Y_POOL_ROLLS  + ROW_H + 4;
    private static final int Y_COL_HEADERS  = Y_LIST_HEADER + 14;
    private static final int Y_FIRST_ROW    = Y_COL_HEADERS + 14;

    // Header widgets.
    private final CycleSelector<String> enabled = new CycleSelector<>(YES_NO, s -> s);
    private final CycleSelector<String> mode = new CycleSelector<>(MODES, s -> s);
    private final CycleSelector<String> strategy = new CycleSelector<>(STRATEGIES, s -> s);
    private final LabeledField poolRolls = new LabeledField("pool rolls", LabeledField.intFilter());
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<Placement> placements = new ArrayList<>();
    private final List<RowWidgets> rowWidgets = new ArrayList<>();

    private MobOriginCreatorScreen parent;
    private int contentX, contentW;
    /** Which row's item is currently being picked (-1 = none). */
    private int editingItemRow = -1;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.drops");
    }
    @Override public Component help() {
        return Component.literal("Per-origin drops layered onto the mob's vanilla loot table.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.contentX = x;
        this.contentW = w;

        if (itemPicker.isOpen()) {
            int pw = Math.min(w - 20, 340), ph = h - 16;
            itemPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }

        placements.clear();
        rowWidgets.clear();
        scroll.setViewport(x, y, w, h);

        Font font = parent.font();
        int labelDx = 8;
        int col1 = labelDx + 90;
        int col2 = w / 2 + 90;

        // Header section.
        place(enabled.build(0, 0, 64, BOX_H), col1, Y_ENABLED + 2);
        place(mode.build(0, 0, 84, BOX_H), col1, Y_MODE_STRAT + 2);
        place(strategy.build(0, 0, 140, BOX_H), col2, Y_MODE_STRAT + 2);
        place(poolRolls.build(font, 0, 0, 56, BOX_H), col1, Y_POOL_ROLLS + 2);

        // Row list — one widget cluster per draft entry.
        MobOriginDraft d = parent.draft();
        for (int i = 0; i < d.dropEntries.size(); i++) {
            buildRow(font, i, w, labelDx);
        }

        // "Add drop" footer button.
        int rows = d.dropEntries.size();
        int afterRowsY = Y_FIRST_ROW + rows * LIST_ROW_H;
        Button add = Button.builder(Component.literal("+ add drop"), b -> addRow())
            .bounds(0, 0, 100, BOX_H + 2).build();
        placeWithActive(add, labelDx, afterRowsY + 2, rows < MAX_ROWS);

        int contentH = afterRowsY + ROW_H + 8;
        scroll.setContentHeight(contentH);
        layout();
    }

    /** Lay out one drop row at logical Y_FIRST_ROW + idx*LIST_ROW_H. */
    private void buildRow(Font font, int idx, int w, int labelDx) {
        int yLogical = Y_FIRST_ROW + idx * LIST_ROW_H;
        int x = labelDx;

        // Pick button.
        final int rowIdx = idx;
        Button pick = Button.builder(Component.literal("pick"), b -> openItemPicker(rowIdx))
            .bounds(0, 0, 32, BOX_H).build();
        place(pick, x, yLogical);

        // Item id text field.
        LabeledField item = new LabeledField("");
        int itemW = Math.min(140, w - labelDx - 32 - 4 - 28 - 28 - 36 - 24 - 28 - 18 - 8 * 6);
        if (itemW < 80) itemW = 80;
        place(item.build(font, 0, 0, itemW, BOX_H), x + 36, yLogical);

        // Count min / max.
        LabeledField min = new LabeledField("", LabeledField.intFilter());
        LabeledField max = new LabeledField("", LabeledField.intFilter());
        place(min.build(font, 0, 0, 28, BOX_H), x + 36 + itemW + 4, yLogical);
        place(max.build(font, 0, 0, 28, BOX_H), x + 36 + itemW + 4 + 30, yLogical);

        // Chance / rolls / weight.
        LabeledField chance = new LabeledField("", LabeledField.doubleFilter());
        LabeledField rolls = new LabeledField("", LabeledField.intFilter());
        LabeledField weight = new LabeledField("", LabeledField.intFilter());
        int cx = x + 36 + itemW + 4 + 30 + 30;
        place(chance.build(font, 0, 0, 36, BOX_H), cx, yLogical);
        place(rolls.build(font, 0, 0, 24, BOX_H), cx + 38, yLogical);
        place(weight.build(font, 0, 0, 28, BOX_H), cx + 64, yLogical);

        // × remove.
        Button remove = Button.builder(Component.literal("x"), b -> removeRow(rowIdx))
            .bounds(0, 0, 16, BOX_H).build();
        place(remove, cx + 94, yLogical);

        rowWidgets.add(new RowWidgets(pick, item, min, max, chance, rolls, weight, remove));
    }

    private void addRow() {
        pushToDraft();
        MobOriginDraft d = parent.draft();
        if (d.dropEntries.size() >= MAX_ROWS) return;
        d.dropEntries.add(new MobOriginDraft.DropRow());
        parent.requestRebuild();
    }

    private void removeRow(int idx) {
        pushToDraft();
        MobOriginDraft d = parent.draft();
        if (idx < 0 || idx >= d.dropEntries.size()) return;
        d.dropEntries.remove(idx);
        parent.requestRebuild();
    }

    private void openItemPicker(int rowIdx) {
        pushToDraft();
        editingItemRow = rowIdx;
        itemPicker.open(false,
            (id, comp) -> {
                if (editingItemRow >= 0 && editingItemRow < parent.draft().dropEntries.size()) {
                    parent.draft().dropEntries.get(editingItemRow).item = id;
                }
            },
            () -> { editingItemRow = -1; parent.requestRebuild(); });
        parent.requestRebuild();
    }

    private void place(AbstractWidget w, int xOffset, int yLogical) {
        placeWithActive(w, xOffset, yLogical, true);
    }

    private void placeWithActive(AbstractWidget w, int xOffset, int yLogical, boolean intendedActive) {
        placements.add(new Placement(w, xOffset, yLogical, intendedActive));
        parent.register(w);
    }

    private void layout() {
        int top = scroll.contentTop();
        int viewTop = scroll.viewTop(), viewBottom = scroll.viewBottom();
        for (Placement p : placements) {
            int screenY = top + p.yLogical;
            p.widget.setX(contentX + p.xOffset);
            p.widget.setY(screenY);
            boolean rowFits = screenY >= viewTop && screenY + p.widget.getHeight() <= viewBottom;
            p.widget.visible = rowFits;
            p.widget.active = rowFits && p.intendedActive;
        }
    }

    @Override
    public void pullFromDraft() {
        if (itemPicker.isOpen()) return;
        MobOriginDraft d = parent.draft();
        enabled.setValue(d.dropsEnabled ? "yes" : "no");
        mode.setValue(d.dropMode);
        strategy.setValue(d.dropStrategy);
        poolRolls.setValue(Integer.toString(d.dropPoolRolls));
        for (int i = 0; i < rowWidgets.size() && i < d.dropEntries.size(); i++) {
            MobOriginDraft.DropRow r = d.dropEntries.get(i);
            RowWidgets rw = rowWidgets.get(i);
            rw.item.setValue(r.item);
            rw.min.setValue(Integer.toString(r.countMin));
            rw.max.setValue(Integer.toString(r.countMax));
            rw.chance.setValue(Double.toString(r.chance));
            rw.rolls.setValue(Integer.toString(r.rolls));
            rw.weight.setValue(Integer.toString(r.weight));
        }
    }

    @Override
    public void pushToDraft() {
        if (itemPicker.isOpen()) return;
        MobOriginDraft d = parent.draft();
        d.dropsEnabled = "yes".equals(enabled.value());
        d.dropMode = mode.value();
        d.dropStrategy = strategy.value();
        d.dropPoolRolls = parseIntOr(poolRolls.value(), d.dropPoolRolls);
        for (int i = 0; i < rowWidgets.size() && i < d.dropEntries.size(); i++) {
            MobOriginDraft.DropRow r = d.dropEntries.get(i);
            RowWidgets rw = rowWidgets.get(i);
            r.item = rw.item.value().trim();
            r.countMin = parseIntOr(rw.min.value(), r.countMin);
            r.countMax = parseIntOr(rw.max.value(), r.countMax);
            r.chance = parseDoubleOr(rw.chance.value(), r.chance);
            r.rolls = parseIntOr(rw.rolls.value(), r.rolls);
            r.weight = parseIntOr(rw.weight.value(), r.weight);
        }
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (itemPicker.isOpen()) return itemPicker.onScroll(mx, my, sy);
        if (mx < contentX || mx > contentX + contentW
            || my < scroll.viewTop() || my > scroll.viewBottom()) return false;
        if (!scroll.onScroll(sy)) return false;
        layout();
        return true;
    }

    @Override
    public void renderBackdrop(GuiGraphics g) {
        if (itemPicker.isOpen()) itemPicker.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (itemPicker.isOpen()) { itemPicker.render(g); return; }

        Font font = parent.font();
        int lx = x + 8;
        int top = scroll.contentTop();
        boolean weighted = "weighted_pool".equals(parent.draft().dropStrategy);

        scroll.beginClip(g);
        CreatorStyle.sectionHeader(g, font, "Drops", lx, top + Y_HEADER, w - 16);
        g.drawString(font, "Enabled",  lx, top + Y_ENABLED + 6,    CreatorStyle.LABEL, false);
        g.drawString(font, "Mode",     lx, top + Y_MODE_STRAT + 6, CreatorStyle.LABEL, false);
        g.drawString(font, "Strategy", x + w / 2 + 6, top + Y_MODE_STRAT + 6, CreatorStyle.LABEL, false);
        g.drawString(font, "Pool rolls",
            lx, top + Y_POOL_ROLLS + 6,
            weighted ? CreatorStyle.LABEL : CreatorStyle.TEXT_DIM, false);

        int rows = parent.draft().dropEntries.size();
        CreatorStyle.sectionHeader(g, font,
            "Entries (" + rows + (rows == MAX_ROWS ? " / " + MAX_ROWS + " max" : "") + ")",
            lx, top + Y_LIST_HEADER, w - 16);

        // Column headers: dim the ones the current strategy ignores.
        int chanceCol = lx + 36 + Math.min(140, w - 8 - 32 - 4 - 28 - 28 - 36 - 24 - 28 - 18 - 8 * 6) + 4 + 30 + 30;
        int rollsCol  = chanceCol + 38;
        int weightCol = chanceCol + 64;
        int countCol  = lx + 36 + Math.min(140, w - 8 - 32 - 4 - 28 - 28 - 36 - 24 - 28 - 18 - 8 * 6) + 4;
        g.drawString(font, "item",   lx + 36, top + Y_COL_HEADERS, CreatorStyle.LABEL, false);
        g.drawString(font, "min",    countCol, top + Y_COL_HEADERS, CreatorStyle.LABEL, false);
        g.drawString(font, "max",    countCol + 30, top + Y_COL_HEADERS, CreatorStyle.LABEL, false);
        g.drawString(font, "chance", chanceCol, top + Y_COL_HEADERS,
            weighted ? CreatorStyle.TEXT_DIM : CreatorStyle.LABEL, false);
        g.drawString(font, "rolls",  rollsCol, top + Y_COL_HEADERS,
            weighted ? CreatorStyle.TEXT_DIM : CreatorStyle.LABEL, false);
        g.drawString(font, "weight", weightCol, top + Y_COL_HEADERS,
            weighted ? CreatorStyle.LABEL : CreatorStyle.TEXT_DIM, false);

        scroll.endClip(g);
        scroll.renderScrollbar(g);
    }

    private record Placement(AbstractWidget widget, int xOffset, int yLogical, boolean intendedActive) {}

    private record RowWidgets(
        Button pick, LabeledField item,
        LabeledField min, LabeledField max,
        LabeledField chance, LabeledField rolls, LabeledField weight,
        Button remove) {}
}
