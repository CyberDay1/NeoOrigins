package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.client.ClientTemplateCache;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Two-tab template picker (Origins / Classes) backed by {@link ClientTemplateCache}.
 * Filters the cached template list by layer for the active tab + a substring
 * search, then hands the chosen {@link OriginTemplate} back through the sink
 * so {@code OriginCreatorScreen} can apply it to the draft.
 *
 * <p>Each row shows the source origin's item icon + display name + id. Same
 * panel/list chrome as {@link SearchPickerOverlay}; tabs sit between the
 * header and the search box.
 */
public final class TemplatePickerOverlay {

    public interface Sink { void accept(OriginTemplate template); }

    private static final int ROW_H = 16;
    private static final int HEADER_H = 18;
    private static final int TAB_H = 16;
    private static final int TAB_TOP = HEADER_H + 4;
    private static final int SEARCH_H = 18;
    private static final int SEARCH_TOP = TAB_TOP + TAB_H + 6;
    private static final int LIST_TOP = SEARCH_TOP + SEARCH_H + 8;

    private boolean open;
    private Sink sink;
    private Runnable onClose;

    private CreatorHost parent;
    private int x, y, w, h, listTop, listH;
    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private final List<OriginTemplate> rowVal = new ArrayList<>();
    private final ScrollPanel scroll = new ScrollPanel();
    private Button tabOriginsBtn, tabClassesBtn;

    private boolean classesTab = false;
    private List<OriginTemplate> source = List.of();
    private List<OriginTemplate> filtered = List.of();
    private int originsCount, classesCount;

    public boolean isOpen() { return open; }

    public void open(Sink sink, Runnable onClose) {
        this.open = true;
        this.sink = sink;
        this.onClose = onClose;
    }

    public void close() { open = false; }

    public void build(CreatorHost parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        rows.clear();
        rowVal.clear();

        source = ClientTemplateCache.all();
        originsCount = (int) source.stream().filter(t -> !t.isClass()).count();
        classesCount = source.size() - originsCount;

        Font font = parent.font();

        // Tab row — left half Origins, right half Classes.
        int halfW = (w - 24) / 2;
        tabOriginsBtn = Button.builder(
                Component.literal("Origins (" + originsCount + ")"),
                b -> { classesTab = false; recompute(); refreshTabActive(); })
            .bounds(x + 8, y + TAB_TOP, halfW, TAB_H).build();
        tabClassesBtn = Button.builder(
                Component.literal("Classes (" + classesCount + ")"),
                b -> { classesTab = true; recompute(); refreshTabActive(); })
            .bounds(x + 8 + halfW + 8, y + TAB_TOP, halfW, TAB_H).build();
        parent.register(tabOriginsBtn);
        parent.register(tabClassesBtn);

        search = new EditBox(font, x + 10, y + SEARCH_TOP, w - 20, SEARCH_H,
            Component.literal("search"));
        search.setHint(Component.literal("type to filter …"));
        search.setResponder(s -> recompute());
        parent.register(search);

        listTop = y + LIST_TOP;
        listH = h - LIST_TOP - 28;
        int visRows = Math.max(1, listH / ROW_H);
        scroll.setViewport(x + 6, listTop, w - 12, listH);

        for (int i = 0; i < visRows; i++) {
            final int slot = i;
            Button b = Button.builder(Component.empty(), btn -> selectSlot(slot))
                .bounds(x + 8 + 18, listTop + i * ROW_H, w - 18 - 18, ROW_H - 1).build();
            rows.add(b);
            rowVal.add(null);
            parent.register(b);
        }
        parent.register(Button.builder(Component.literal("Cancel"), b -> cancel())
            .bounds(x + w - 66, y + h - 21, 60, 18).build());

        refreshTabActive();
        recompute();
    }

    private void refreshTabActive() {
        if (tabOriginsBtn != null) tabOriginsBtn.active = classesTab;
        if (tabClassesBtn != null) tabClassesBtn.active = !classesTab;
    }

    private void recompute() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<OriginTemplate> base = new ArrayList<>(source.size());
        for (OriginTemplate t : source) {
            if (t.isClass() != classesTab) continue;
            if (q.isEmpty()
                    || t.originId().toString().toLowerCase(Locale.ROOT).contains(q)
                    || t.displayName().toLowerCase(Locale.ROOT).contains(q)) {
                base.add(t);
            }
        }
        filtered = base;
        scroll.setContentHeight(filtered.size() * ROW_H);
        refreshRows();
    }

    private void refreshRows() {
        int first = Math.max(0, (scroll.viewTop() - scroll.contentTop()) / ROW_H);
        for (int i = 0; i < rows.size(); i++) {
            int idx = first + i;
            Button b = rows.get(i);
            if (idx < filtered.size()) {
                OriginTemplate t = filtered.get(idx);
                rowVal.set(i, t);
                b.setMessage(Component.literal(
                    t.displayName() + "   " + t.originId().toString()));
                b.visible = true;
                b.active = true;
            } else {
                rowVal.set(i, null);
                b.visible = false;
                b.active = false;
            }
        }
    }

    private void selectSlot(int slot) {
        if (slot >= rowVal.size()) return;
        OriginTemplate t = rowVal.get(slot);
        if (t == null || sink == null) return;
        Sink s = sink;
        Runnable oc = onClose;
        close();
        s.accept(t);
        if (oc != null) oc.run();
    }

    private void cancel() {
        Runnable oc = onClose;
        close();
        if (oc != null) oc.run();
    }

    public void renderBackdrop(GuiGraphics g) {
        if (!open || parent == null) return;
        Font font = parent.font();

        g.fill(0, 0, parent.hostWidth(), parent.hostHeight(), CreatorStyle.SCRIM);
        CreatorStyle.panel(g, x, y, w, h);

        g.fill(x + 1, y + 1, x + w - 1, y + HEADER_H, CreatorStyle.PANEL_BG);
        String title = classesTab ? "Load class template" : "Load origin template";
        g.drawCenteredString(font,
            Component.literal(title + "  (" + filtered.size() + ")"),
            x + w / 2, y + 5, CreatorStyle.SECTION);
        CreatorStyle.divider(g, x + 1, y + HEADER_H, w - 2);

        // Accent under the active tab so the user can see which list they're in.
        int halfW = (w - 24) / 2;
        int activeX = x + 8 + (classesTab ? halfW + 8 : 0);
        g.fill(activeX, y + TAB_TOP + TAB_H, activeX + halfW,
            y + TAB_TOP + TAB_H + 2, CreatorStyle.ACCENT);

        // Search-box frame.
        int sx = x + 8, sy = y + SEARCH_TOP - 2, sw = w - 16, sh = SEARCH_H + 4;
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF05050C);
        g.renderOutline(sx, sy, sw, sh, CreatorStyle.ACCENT);

        // List background.
        g.fill(x + 4, listTop - 2, x + w - 4, listTop + listH + 2, CreatorStyle.PANEL_BG2);
        // Per-row icon (drawn behind the row button text).
        int first = Math.max(0, (scroll.viewTop() - scroll.contentTop()) / ROW_H);
        for (int i = 0; i < rows.size(); i++) {
            int idx = first + i;
            if (idx >= filtered.size()) continue;
            OriginTemplate t = filtered.get(idx);
            ItemStack stack = stackFor(t.icon());
            int iconX = x + 8;
            int iconY = listTop + i * ROW_H - 1;
            g.renderItem(stack, iconX, iconY);
            if ((i & 1) == 0) {
                g.fill(x + 26, listTop + i * ROW_H, x + w - 12,
                    listTop + (i + 1) * ROW_H - 1, 0x14FFFFFF);
            }
        }
        scroll.renderScrollbar(g);

        if (source.isEmpty()) {
            g.drawCenteredString(font,
                Component.literal("No templates available, server hasn't sent any."),
                x + w / 2, listTop + listH / 2 - 4, CreatorStyle.TEXT_DIM);
        } else if (filtered.isEmpty()) {
            g.drawCenteredString(font,
                Component.literal(classesTab
                    ? "No classes match." : "No origins match."),
                x + w / 2, listTop + listH / 2 - 4, CreatorStyle.TEXT_DIM);
        }
    }

    public void render(GuiGraphics g) {}

    public boolean onScroll(double mx, double my, double sy) {
        if (!open) return false;
        if (mx < x || mx > x + w || my < listTop || my > listTop + listH) return false;
        if (scroll.onScroll(sy)) { refreshRows(); return true; }
        return false;
    }

    private static ItemStack stackFor(ResourceLocation id) {
        if (id == null) return new ItemStack(Items.PLAYER_HEAD);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PLAYER_HEAD);
        return new ItemStack(item);
    }

    /** Adapter so callers can pass a method ref of the right shape without
     *  importing {@link Sink} directly. */
    public Consumer<OriginTemplate> asConsumer() { return t -> { if (sink != null) sink.accept(t); }; }
}
