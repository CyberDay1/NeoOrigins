package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Registry-backed item picker drawn on top of a creator tab: a search box over
 * every registered item id (modded items appear for free via
 * {@code BuiltInRegistries.ITEM}), a virtualized id list, and an optional SNBT
 * {@code components} field. No item icons in v1 — a filtered text list, matching
 * the in-repo vanilla-widget style.
 *
 * <p>Tab-driven: the owning tab flips {@link #open} and, while {@link #isOpen()},
 * builds <em>only</em> this overlay's widgets (so its row buttons get the
 * screen's click routing for free) and calls {@link #render}/{@link #onScroll}.
 * Selecting an id (or cancelling) invokes the {@link Sink} and closes; the tab
 * then {@code requestRebuild()}s back to its normal widgets.
 */
public final class ItemPickerOverlay {

    /** Receives the chosen item id and the SNBT components text ("" if unused). */
    public interface Sink { void accept(String itemId, String componentsSnbt); }

    private static final int ROW_H = 13;

    private boolean open;
    private boolean wantComponents;
    private Sink sink;
    private Runnable onClose;

    private List<String> allIds;          // sorted, cached on first open
    private List<String> filtered = List.of();

    private OriginCreatorScreen parent;
    private int x, y, w, h, listTop, listH, visRows;
    private EditBox search, components;
    private final java.util.List<Button> rows = new java.util.ArrayList<>();
    private final java.util.List<String> rowId = new java.util.ArrayList<>();
    private final ScrollPanel scroll = new ScrollPanel();

    public boolean isOpen() { return open; }

    /**
     * Open the picker; {@code withComponents} adds the SNBT components field.
     * {@code onClose} runs after select <em>or</em> cancel so the owning tab can
     * rebuild back to its normal widgets.
     */
    public void open(boolean withComponents, Sink sink, Runnable onClose) {
        this.open = true;
        this.wantComponents = withComponents;
        this.sink = sink;
        this.onClose = onClose;
    }

    public void close() { open = false; }

    /** Build the overlay widgets in {@code (x,y,w,h)}; call only while open. */
    public void build(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        rows.clear();
        rowId.clear();
        if (allIds == null) {
            allIds = BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString).sorted().toList();
        }

        Font font = parent.font();
        search = new EditBox(font, x + 6, y + 6, w - 12, 16, Component.literal("search"));
        search.setResponder(s -> recompute());
        parent.register(search);

        int bottomH = wantComponents ? 44 : 22;
        listTop = y + 28;
        listH = h - 28 - bottomH;
        visRows = Math.max(1, listH / ROW_H);
        scroll.setViewport(x + 4, listTop, w - 8, listH);

        for (int i = 0; i < visRows; i++) {
            final int slot = i;
            Button b = Button.builder(Component.empty(), btn -> selectSlot(slot))
                .bounds(x + 6, listTop + i * ROW_H, w - 14, ROW_H - 1).build();
            rows.add(b);
            rowId.add("");
            parent.register(b);
        }

        if (wantComponents) {
            components = new EditBox(font, x + 6, y + h - 40, w - 12, 16,
                Component.literal("components snbt"));
            components.setMaxLength(32767);
            parent.register(components);
        }
        parent.register(Button.builder(Component.literal("cancel"), b -> cancel())
            .bounds(x + w - 60, y + h - 20, 54, 18).build());

        recompute();
    }

    private void recompute() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = q.isEmpty() ? allIds
            : allIds.stream().filter(s -> s.contains(q)).toList();
        scroll.setContentHeight(filtered.size() * ROW_H);
        refreshRows();
    }

    /** Map the fixed button pool onto the current filtered+scrolled window. */
    private void refreshRows() {
        int first = scrollFirstIndex();
        for (int i = 0; i < rows.size(); i++) {
            int idx = first + i;
            Button b = rows.get(i);
            if (idx < filtered.size()) {
                String id = filtered.get(idx);
                rowId.set(i, id);
                b.setMessage(Component.literal(id));
                b.visible = true;
                b.active = true;
            } else {
                rowId.set(i, "");
                b.visible = false;
                b.active = false;
            }
        }
    }

    private int scrollFirstIndex() {
        return Math.max(0, (scroll.viewTop() - scroll.contentTop()) / ROW_H);
    }

    private void selectSlot(int slot) {
        if (slot >= rowId.size()) return;
        String id = rowId.get(slot);
        if (id.isEmpty() || sink == null) return;
        Sink s = sink;
        Runnable oc = onClose;
        String comp = wantComponents && components != null ? components.getValue().trim() : "";
        close();
        s.accept(id, comp);
        if (oc != null) oc.run();
    }

    private void cancel() {
        Runnable oc = onClose;
        close();
        if (oc != null) oc.run();
    }

    public void render(GuiGraphics g) {
        if (!open || parent == null) return;
        Font font = parent.font();
        g.fill(0, 0, parent.width, parent.height, 0xCC000010);
        g.fill(x, y, x + w, y + h, 0xFF0B0B1A);
        g.renderOutline(x, y, w, h, 0xFF3A3A5A);
        g.drawString(font, "pick item (" + filtered.size() + ")",
            x + 6, y - 10, 0xFFBBBBCC, false);
        if (wantComponents) {
            g.drawString(font, "components (SNBT, optional)",
                x + 6, y + h - 52, 0xFF8888AA, false);
        }
        scroll.renderScrollbar(g);
    }

    public boolean onScroll(double mx, double my, double sy) {
        if (!open) return false;
        if (mx < x || mx > x + w || my < listTop || my > listTop + listH) return false;
        if (scroll.onScroll(sy)) { refreshRows(); return true; }
        return false;
    }
}
