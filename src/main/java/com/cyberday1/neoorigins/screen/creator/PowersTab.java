package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.PowerFormPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Powers tab — the generic per-power editor. Owns the draft's power list
 * (add / remove / navigate) and the type picker; the selected power's body is
 * rendered by a shared {@link PowerFormPanel} (schema/codec hybrid form +
 * raw-JSON escape). The Appearance tab reuses the same panel scoped to the
 * Tier-A visual powers.
 */
public final class PowersTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.powers");

    private static final int HDR_H = 20, LABEL_DX = 6, FIELD_DX = 140;

    private final List<String> allTypes = FormModel.allTypes();
    private final PowerFormPanel form = new PowerFormPanel();

    private OriginCreatorScreen parent;
    private int x, y, w, h;
    private int sel = 0;       // index into draft.powers
    private boolean rawMode;
    private int typeIdx;

    @Override public Component title() { return TITLE; }

    private List<PowerDraft> powers() { return parent.draft().powers; }

    private PowerDraft current() {
        List<PowerDraft> p = powers();
        return (sel >= 0 && sel < p.size()) ? p.get(sel) : null;
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;

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

        typeIdx = Math.max(0, allTypes.indexOf(p.typeId));
        int r2 = y + HDR_H + 4;
        parent.register(Button.builder(typeLabel(), b -> cycleType())
            .bounds(x + FIELD_DX, r2, Math.min(w - FIELD_DX - 60, 220), HDR_H).build());
        parent.register(Button.builder(
                Component.literal(rawMode ? "form" : "raw"), b -> toggleRaw())
            .bounds(x + w - 52, r2, 48, HDR_H).build());

        int formTop = r2 + HDR_H + 12;
        form.init(parent, p, rawMode, x, formTop, w, (y + h) - formTop);
    }

    // ── header actions (capture form → mutate → rebuild) ────────────────────

    private void step(int d) {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        pushToDraft();
        sel = Math.floorMod(sel + d, ps.size());
        parent.requestRebuild();
    }

    private void addPower() {
        pushToDraft();
        String type = allTypes.isEmpty() ? "neoorigins:flight" : allTypes.get(0);
        PowerDraft np = new PowerDraft(null, type);
        np.rawJson = "{}";
        powers().add(np);
        sel = powers().size() - 1;
        rawMode = false;
        parent.requestRebuild();
    }

    private void removePower() {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        ps.remove(sel);
        sel = Math.max(0, Math.min(sel, ps.size() - 1));
        parent.requestRebuild();
    }

    private void cycleType() {
        PowerDraft p = current();
        if (p == null || allTypes.isEmpty()) return;
        pushToDraft();
        typeIdx = (typeIdx + 1) % allTypes.size();
        p.typeId = allTypes.get(typeIdx);
        p.rawJson = "{}"; // fields differ per type — start clean
        parent.requestRebuild();
    }

    private void toggleRaw() {
        if (current() == null) return;
        pushToDraft();
        rawMode = !rawMode;
        parent.requestRebuild();
    }

    // ── draft sync ──────────────────────────────────────────────────────────

    @Override public void pullFromDraft() { form.pull(); }

    @Override
    public void pushToDraft() {
        PowerDraft p = current();
        if (p == null) return;
        if (!allTypes.isEmpty()) {
            p.typeId = allTypes.get(Math.max(0, Math.min(typeIdx, allTypes.size() - 1)));
        }
        p.powerId = parent.draft().mintPowerId(p, p.typeId);
        form.push();
    }

    private Component typeLabel() {
        return Component.literal(allTypes.isEmpty() ? "(no types)"
            : allTypes.get(Math.max(0, Math.min(typeIdx, allTypes.size() - 1))));
    }

    // ── render ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        List<PowerDraft> ps = powers();

        if (ps.isEmpty()) {
            g.centeredText(font,
                Component.literal("No powers yet — click \"+ add\""),
                x + w / 2, y + h / 2 - 4, 0xFF8888AA);
            return;
        }

        g.text(font, "power " + (sel + 1) + "/" + ps.size(),
            x + FIELD_DX, y + 6, 0xFFBBBBCC, false);
        PowerDraft p = current();
        if (p != null && p.powerId != null) {
            g.text(font, p.powerId.toString(),
                x + LABEL_DX, y + HDR_H + 8, 0xFF6E6E92, false);
        }
        form.render(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return form.onScroll(mx, my, sy);
    }
}
