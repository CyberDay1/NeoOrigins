package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Reusable search-and-pick overlay: a titled panel, a prominent search box,
 * and a scrollable, virtualized, zebra-striped list filtered by substring.
 *
 * <p>The chrome is painted in {@link #renderBackdrop} (called by the screen
 * <em>before</em> widgets render) so the search box and row buttons sit on top
 * of the panel instead of being covered by it.
 */
public final class SearchPickerOverlay {

    public interface Sink { void accept(String value); }

    private static final int ROW_H = 13;
    private static final int HEADER_H = 18;
    private static final int SEARCH_H = 18;
    private static final int SEARCH_TOP = HEADER_H + 6;
    private static final int LIST_TOP = SEARCH_TOP + SEARCH_H + 8;

    private boolean open;
    private String title = "pick";
    private Sink sink;
    private Runnable onClose;
    private Supplier<List<String>> source = List::of;
    private int bottomReserve = 24;

    private List<String> all = List.of();
    private List<String> filtered = List.of();

    private OriginCreatorScreen parent;
    private int x, y, w, h, listTop, listH;
    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private final List<String> rowVal = new ArrayList<>();
    private final ScrollPanel scroll = new ScrollPanel();

    public boolean isOpen() { return open; }

    public void open(String title, Supplier<List<String>> source, Sink sink, Runnable onClose) {
        this.open = true;
        this.title = title;
        this.source = source;
        this.sink = sink;
        this.onClose = onClose;
    }

    public void close() { open = false; }

    public void setBottomReserve(int px) { this.bottomReserve = px; }

    public int listBottom() { return listTop + listH; }

    public void build(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        rows.clear();
        rowVal.clear();
        all = source.get();

        Font font = parent.font();
        search = new EditBox(font, x + 10, y + SEARCH_TOP, w - 20, SEARCH_H,
            Component.literal("search"));
        search.setHint(Component.literal("type to filter …"));
        search.setResponder(s -> recompute());
        parent.register(search);

        listTop = y + LIST_TOP;
        listH = h - LIST_TOP - bottomReserve;
        int visRows = Math.max(1, listH / ROW_H);
        scroll.setViewport(x + 6, listTop, w - 12, listH);

        for (int i = 0; i < visRows; i++) {
            final int slot = i;
            Button b = Button.builder(Component.empty(), btn -> selectSlot(slot))
                .bounds(x + 8, listTop + i * ROW_H, w - 18, ROW_H - 1).build();
            rows.add(b);
            rowVal.add("");
            parent.register(b);
        }
        parent.register(Button.builder(Component.literal("Cancel"), b -> cancel())
            .bounds(x + w - 66, y + h - 21, 60, 18).build());

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

    /** Panel + header + search frame + list zebra — drawn before the widgets. */
    public void renderBackdrop(GuiGraphics g) {
        if (!open || parent == null) return;
        Font font = parent.font();

        g.fill(0, 0, parent.width, parent.height, CreatorStyle.SCRIM);
        CreatorStyle.panel(g, x, y, w, h);

        // Header strip + title.
        g.fill(x + 1, y + 1, x + w - 1, y + HEADER_H, CreatorStyle.PANEL_BG);
        g.drawCenteredString(font,
            Component.literal(title + "  (" + filtered.size() + ")"),
            x + w / 2, y + 5, CreatorStyle.SECTION);
        CreatorStyle.divider(g, x + 1, y + HEADER_H, w - 2);

        // Pronounced search box: accent frame behind the EditBox widget.
        int sx = x + 8, sy = y + SEARCH_TOP - 2, sw = w - 16, sh = SEARCH_H + 4;
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF05050C);
        g.renderOutline(sx, sy, sw, sh, CreatorStyle.ACCENT);

        // List background + zebra rows.
        g.fill(x + 4, listTop - 2, x + w - 4, listTop + listH + 2, CreatorStyle.PANEL_BG2);
        for (int i = 0; i < rows.size(); i++) {
            if (rowVal.get(i).isEmpty()) continue;
            if ((i & 1) == 0) {
                g.fill(x + 6, listTop + i * ROW_H, x + w - 12,
                    listTop + (i + 1) * ROW_H - 1, 0x14FFFFFF);
            }
        }
        scroll.renderScrollbar(g);
    }

    /** Foreground (above widgets) — nothing extra needed. */
    public void render(GuiGraphics g) {}

    public boolean onScroll(double mx, double my, double sy) {
        if (!open) return false;
        if (mx < x || mx > x + w || my < listTop || my > listTop + listH) return false;
        if (scroll.onScroll(sy)) { refreshRows(); return true; }
        return false;
    }
}
