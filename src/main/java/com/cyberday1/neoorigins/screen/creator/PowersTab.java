package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.PowerFormPanel;
import com.cyberday1.neoorigins.screen.creator.widget.SearchPickerOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Powers tab — the generic per-power editor. Owns the draft's power list
 * (add / remove / navigate) and a searchable power-type picker; the selected
 * power's body is rendered by a shared {@link PowerFormPanel} (guided field
 * form, or a JSON escape hatch for anything the form can't express).
 */
public final class PowersTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.powers");
    private static final Component HELP = Component.literal(
        "Add powers to your origin. Pick a type, fill the fields, or edit JSON.");

    private static final int HDR_H = 20, LABEL_DX = 6, FIELD_DX = 140;

    private final PowerFormPanel form = new PowerFormPanel();
    private final SearchPickerOverlay typePicker = new SearchPickerOverlay();

    private OriginCreatorScreen parent;
    private int x, y, w, h;
    private int sel = 0;       // index into draft.powers
    private boolean rawMode;

    @Override public Component title() { return TITLE; }
    @Override public Component help() { return HELP; }

    private List<PowerDraft> powers() { return parent.draft().powers; }

    private PowerDraft current() {
        List<PowerDraft> p = powers();
        return (sel >= 0 && sel < p.size()) ? p.get(sel) : null;
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;

        if (typePicker.isOpen()) {           // picker owns input while open
            int pw = Math.min(w - 20, 320), ph = h - 16;
            typePicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }

        List<PowerDraft> ps = powers();
        if (sel >= ps.size()) sel = Math.max(0, ps.size() - 1);

        int bx = x + LABEL_DX, by = y;
        parent.register(Button.builder(Component.literal("<"), b -> step(-1))
            .bounds(bx, by, 18, HDR_H).build());
        parent.register(Button.builder(Component.literal(">"), b -> step(1))
            .bounds(bx + 20, by, 18, HDR_H).build());
        parent.register(Button.builder(Component.literal("+ add"), b -> addPower())
            .bounds(bx + 42, by, 44, HDR_H).build());
        parent.register(Button.builder(Component.literal("- del"), b -> removePower())
            .bounds(bx + 88, by, 44, HDR_H).build());

        PowerDraft p = current();
        if (p == null) return; // empty list — render() shows the hint

        int r2 = y + HDR_H + 4;
        parent.register(Button.builder(
                Component.literal("Type: " + shortType(p.typeId) + "  (change)"),
                b -> openTypePicker())
            .bounds(x + LABEL_DX, r2, Math.min(w - 70, 300), HDR_H).build());
        parent.register(Button.builder(
                Component.literal(rawMode ? "Use form" : "Edit JSON"), b -> toggleRaw())
            .bounds(x + w - 78, r2, 74, HDR_H).build());

        int formTop = r2 + HDR_H + 14;
        form.init(parent, p, rawMode, x, formTop, w, (y + h) - formTop);
    }

    // ── header actions ──────────────────────────────────────────────────────

    private void step(int d) {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        pushToDraft();
        sel = Math.floorMod(sel + d, ps.size());
        parent.requestRebuild();
    }

    /** "+ add" picks the type first, then appends the power. */
    private void addPower() {
        pushToDraft();
        typePicker.open("pick a power type", FormModel::creatorTypes,
            type -> {
                PowerDraft np = new PowerDraft(null, type);
                np.rawJson = "{}";
                powers().add(np);
                sel = powers().size() - 1;
                rawMode = false;
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void removePower() {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        ps.remove(sel);
        sel = Math.max(0, Math.min(sel, ps.size() - 1));
        parent.requestRebuild();
    }

    private void openTypePicker() {
        PowerDraft p = current();
        if (p == null) return;
        pushToDraft();
        typePicker.open("pick a power type", FormModel::creatorTypes,
            type -> {
                PowerDraft cur = current();
                if (cur != null && !type.equals(cur.typeId)) {
                    cur.typeId = type;
                    cur.rawJson = "{}"; // fields differ per type — start clean
                }
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void toggleRaw() {
        if (current() == null) return;
        pushToDraft();
        rawMode = !rawMode;
        parent.requestRebuild();
    }

    // ── draft sync ──────────────────────────────────────────────────────────

    @Override public void pullFromDraft() { if (!typePicker.isOpen()) form.pull(); }

    @Override
    public void pushToDraft() {
        if (typePicker.isOpen()) return;
        PowerDraft p = current();
        if (p == null) return;
        p.powerId = parent.draft().mintPowerId(p, p.typeId);
        form.push();
    }

    private static String shortType(String typeId) {
        int c = typeId.indexOf(':');
        return c >= 0 ? typeId.substring(c + 1) : typeId;
    }

    // ── render ──────────────────────────────────────────────────────────────

    @Override
    public void renderBackdrop(GuiGraphics g) {
        if (typePicker.isOpen()) typePicker.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (typePicker.isOpen()) { typePicker.render(g); return; }

        Font font = parent.font();
        List<PowerDraft> ps = powers();

        if (ps.isEmpty()) {
            CreatorStyle.emptyState(g, font,
                "No powers yet — click \"+ add\" to choose one", x + w / 2, y + h / 2 - 4);
            return;
        }

        g.drawString(font, "power " + (sel + 1) + "/" + ps.size(),
            x + FIELD_DX, y + 6, CreatorStyle.TEXT_DIM, false);
        PowerDraft p = current();
        if (p != null && p.powerId != null) {
            g.drawString(font, "id: " + p.powerId,
                x + LABEL_DX, y + HDR_H + 4, CreatorStyle.TEXT_DIM, false);
        }
        CreatorStyle.divider(g, x + 4, y + HDR_H * 2 + 2, w - 8);
        form.render(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (typePicker.isOpen()) return typePicker.onScroll(mx, my, sy);
        return form.onScroll(mx, my, sy);
    }
}
