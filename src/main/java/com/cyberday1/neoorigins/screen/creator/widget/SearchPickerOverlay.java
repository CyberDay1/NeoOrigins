package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Reusable search-and-pick overlay: a title, a search box, and a scrollable,
 * virtualized list of strings filtered by substring. Selecting an entry runs
 * the sink and closes; cancelling just closes. The owning tab flips it open
 * and, while {@link #isOpen()}, builds <em>only</em> this overlay's widgets so
 * its row buttons get the screen's click routing.
 *
 * <p>Backs both the power-type picker (Powers tab) and the item picker
 * ({@link ItemPickerOverlay} composes this and adds the SNBT components field).
 */
public final class SearchPickerOverlay {

    public interface Sink { void accept(String value); }

    private static final int ROW_H = 13;

    private boolean open;
    private String title = "pick";
    private Sink sink;
    private Runnable onClose;
    private Supplier<List<String>> source = List::of;
    private int bottomReserve = 22; // space the cancel row needs

    private List<String> all = List.of();
    private List<String> filtered = List.of();

    private OriginCreatorScreen parent;
    private int x, y, w, h, listTop, listH;
    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private final List<String> rowVal = new ArrayList<>();
    private final ScrollPanel scroll = new ScrollPanel();

    public boolean isOpen() { return open; }

    /**
     * Open the picker. {@code source} is queried lazily on build (fresh each
     * open); {@code onClose} runs after select or cancel so the tab rebuilds.
     */
    public void open(String title, Supplier<List<String>> source, Sink sink, Runnable onClose) {
        this.open = true;
        this.title = title;
        this.source = source;
        this.sink = sink;
        this.onClose = onClose;
    }

    public void close() { open = false; }

    /** Extra bottom space (px) the host overlay needs below the list. */
    public void setBottomReserve(int px) { this.bottomReserve = px; }

    public int listBottom() { return listTop + listH; }

    public void build(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        rows.clear();
        rowVal.clear();
        all = source.get();

        Font font = parent.font();
        search = new EditBox(font, x + 6, y + 6, w - 12, 16, Component.literal("search"));
        search.setResponder(s -> recompute());
        parent.register(search);

        listTop = y + 28;
        listH = h - 28 - bottomReserve;
        int visRows = Math.max(1, listH / ROW_H);
        scroll.setViewport(x + 4, listTop, w - 8, listH);

        for (int i = 0; i < visRows; i++) {
            final int slot = i;
            Button b = Button.builder(Component.empty(), btn -> selectSlot(slot))
                .bounds(x + 6, listTop + i * ROW_H, w - 14, ROW_H - 1).build();
            rows.add(b);
            rowVal.add("");
            parent.register(b);
        }
        parent.register(Button.builder(Component.literal("cancel"), b -> cancel())
            .bounds(x + w - 60, y + h - 20, 54, 18).build());

        recompute();
    }

    private void recompute() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = q.isEmpty() ? all
            : all.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(q)).toList();
        scroll.setContentHeight(filtered.size() * ROW_H);
        refreshRows();
    }

    private void refreshRows() {
        int first = Math.max(0, (scroll.viewTop() - scroll.contentTop()) / ROW_H);
        for (int i = 0; i < rows.size(); i++) {
            int idx = first + i;
            Button b = rows.get(i);
            if (idx < filtered.size()) {
                String v = filtered.get(idx);
                rowVal.set(i, v);
                b.setMessage(Component.literal(v));
                b.visible = true;
                b.active = true;
            } else {
                rowVal.set(i, "");
                b.visible = false;
                b.active = false;
            }
        }
    }

    private void selectSlot(int slot) {
        if (slot >= rowVal.size()) return;
        String v = rowVal.get(slot);
        if (v.isEmpty() || sink == null) return;
        Sink s = sink;
        Runnable oc = onClose;
        close();
        s.accept(v);
        if (oc != null) oc.run();
    }

    private void cancel() {
        Runnable oc = onClose;
        close();
        if (oc != null) oc.run();
    }

    public void render(GuiGraphicsExtractor g) {
        if (!open || parent == null) return;
        Font font = parent.font();
        g.fill(0, 0, parent.width, parent.height, 0xCC000010);
        g.fill(x, y, x + w, y + h, 0xFF0B0B1A);
        g.outline(x, y, w, h, 0xFF3A3A5A);
        g.text(font, title + "  (" + filtered.size() + " — type to filter, Esc-free: click cancel)",
            x + 6, y - 10, 0xFFBBBBCC, false);
        scroll.renderScrollbar(g);
    }

    public boolean onScroll(double mx, double my, double sy) {
        if (!open) return false;
        if (mx < x || mx > x + w || my < listTop || my > listTop + listH) return false;
        if (scroll.onScroll(sy)) { refreshRows(); return true; }
        return false;
    }
}
