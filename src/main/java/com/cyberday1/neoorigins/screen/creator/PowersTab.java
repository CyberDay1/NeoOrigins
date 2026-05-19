package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory.FieldRow;
import com.cyberday1.neoorigins.screen.creator.widget.ScrollPanel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Powers tab — the schema/codec hybrid form renderer. Holds the draft's power
 * list (add / remove / pick type), renders the selected power's fields via
 * {@link FormModel} → {@link FieldWidgetFactory} inside a {@link ScrollPanel},
 * and offers a per-power raw-JSON escape hatch.
 *
 * <p>Each power's body is stored in {@link PowerDraft#rawJson} (the config
 * object, no {@code type} — the serializer injects that). The form is the
 * authoritative editor when fields resolve; raw mode round-trips the exact
 * JSON for anything the form can't express (arrays / nested objects / DSL refs).
 */
public final class PowersTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.powers");

    private static final int HDR_H = 20, ROW_H = 22, LABEL_DX = 6, FIELD_DX = 140;

    private final List<String> allTypes = FormModel.allTypes();

    private OriginCreatorScreen parent;
    private int x, y, w, h;
    private int sel = 0;            // index into draft.powers
    private boolean rawMode;
    private int typeIdx;

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<FieldRow> rows = new ArrayList<>();
    private EditBox rawBox;         // built only in raw mode
    private int formTop;            // y of the scroll viewport

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
        rows.clear();
        rawBox = null;
        Font font = parent.font();

        List<PowerDraft> ps = powers();
        if (sel >= ps.size()) sel = Math.max(0, ps.size() - 1);

        // ── header: power nav + add/remove ────────────────────────────────
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
        // ── row 2: type picker + raw toggle ───────────────────────────────
        int r2 = y + HDR_H + 4;
        parent.register(Button.builder(typeLabel(), b -> cycleType())
            .bounds(x + FIELD_DX, r2, Math.min(w - FIELD_DX - 60, 220), HDR_H).build());
        parent.register(Button.builder(
                Component.literal(rawMode ? "form" : "raw"), b -> toggleRaw())
            .bounds(x + w - 52, r2, 48, HDR_H).build());

        formTop = r2 + HDR_H + 6;
        int viewH = (y + h) - formTop;
        scroll.setViewport(x, formTop, w, viewH);

        if (rawMode) {
            rawBox = new EditBox(font, x + LABEL_DX, formTop, w - LABEL_DX - 6,
                viewH - 4, Component.literal("raw json"));
            rawBox.setMaxLength(32767);
            parent.register(rawBox);
            scroll.setContentHeight(0);
        } else {
            buildForm(p, font);
        }
        layoutRows();
    }

    private void buildForm(PowerDraft p, Font font) {
        ResourceLocation type = parseType(p.typeId);
        List<com.cyberday1.neoorigins.power.schemaform.FormFieldSpec> specs;
        try {
            specs = type == null ? List.of() : FormModel.forPower(type);
        } catch (RuntimeException e) {
            specs = List.of(); // unresolvable — raw toggle still covers it
        }
        int fieldW = Math.min(w - FIELD_DX - 12, 240);
        for (var spec : specs) {
            FieldRow row = FieldWidgetFactory.create(spec);
            row.build(parent, font, fieldW, 16);
            rows.add(row);
        }
        scroll.setContentHeight(rows.size() * ROW_H + 4);
    }

    /** Re-place form widgets against the scroll offset; hide off-view rows. */
    private void layoutRows() {
        int fieldX = x + FIELD_DX;
        int top = scroll.contentTop();
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = top + i * ROW_H;
            FieldRow row = rows.get(i);
            row.reposition(fieldX, rowTop);
            row.setVisible(!rawMode && scroll.rowVisible(rowTop, ROW_H));
        }
    }

    // ── header actions (all: capture form → mutate → rebuild) ───────────────

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
        pushToDraft();        // serialize current mode into the draft
        rawMode = !rawMode;
        parent.requestRebuild();
    }

    // ── draft sync ──────────────────────────────────────────────────────────

    @Override
    public void pullFromDraft() {
        PowerDraft p = current();
        if (p == null) return;
        if (rawMode) {
            if (rawBox != null) rawBox.setValue(p.rawJson == null ? "{}" : p.rawJson);
            return;
        }
        JsonObject body = parseObject(p.rawJson);
        for (FieldRow row : rows) row.fromJson(body.get(row.fieldName()));
    }

    @Override
    public void pushToDraft() {
        PowerDraft p = current();
        if (p == null) return;
        p.typeId = allTypes.isEmpty() ? p.typeId
            : allTypes.get(Math.max(0, Math.min(typeIdx, allTypes.size() - 1)));
        p.powerId = mintId(p);
        if (rawMode) {
            if (rawBox != null) p.rawJson = rawBox.getValue();
        } else {
            JsonObject body = new JsonObject();
            for (FieldRow row : rows) {
                JsonElement v = row.toJson();
                if (v != null) body.add(row.fieldName(), v);
            }
            p.rawJson = body.toString();
        }
    }

    /** {@code <idPath>_<typeShortName>} under the custom namespace; dup types
     *  get {@code _2}, {@code _3}, … so each power id stays unique in the pack. */
    private ResourceLocation mintId(PowerDraft self) {
        String typeShort = parseType(self.typeId) != null
            ? parseType(self.typeId).getPath() : "power";
        String base = sanitize(parent.draft().idPath) + "_" + typeShort;
        String candidate = base;
        int n = 1;
        boolean clash;
        do {
            clash = false;
            for (PowerDraft other : powers()) {
                if (other == self) continue;
                if (other.powerId != null
                        && other.powerId.getPath().equals(candidate)) { clash = true; break; }
            }
            if (clash) candidate = base + "_" + (++n);
        } while (clash);
        return ResourceLocation.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, candidate);
    }

    private static String sanitize(String s) {
        String v = s == null ? "" : s.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        return v.isEmpty() ? "origin" : v;
    }

    private static ResourceLocation parseType(String s) {
        try { return ResourceLocation.parse(s); }
        catch (RuntimeException e) { return null; }
    }

    private static JsonObject parseObject(String s) {
        try {
            JsonElement el = JsonParser.parseString(s == null || s.isBlank() ? "{}" : s);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) { return new JsonObject(); }
    }

    private Component typeLabel() {
        return Component.literal(allTypes.isEmpty() ? "(no types)"
            : allTypes.get(Math.max(0, Math.min(typeIdx, allTypes.size() - 1))));
    }

    // ── render ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        List<PowerDraft> ps = powers();

        if (ps.isEmpty()) {
            g.drawCenteredString(font,
                Component.literal("No powers yet — click \"+ add\""),
                x + w / 2, y + h / 2 - 4, 0xFF8888AA);
            return;
        }

        PowerDraft p = current();
        // header status: index + minted id + form/raw source
        g.drawString(font, "power " + (sel + 1) + "/" + ps.size(),
            x + 140, y + 6, 0xFFBBBBCC, false);
        if (p != null && p.powerId != null) {
            g.drawString(font, p.powerId.toString(),
                x + LABEL_DX, y + HDR_H + 8, 0xFF6E6E92, false);
        }
        boolean schemaBacked = p != null && parseType(p.typeId) != null
            && FormModel.isSchemaBacked(parseType(p.typeId));
        g.drawString(font, schemaBacked ? "schema" : "codec",
            x + w - 52, y + HDR_H + 8, 0xFF6E6E92, false);

        if (rawMode || rows.isEmpty()) {
            if (rawMode) g.drawString(font,
                "raw config JSON (no \"type\" key — injected on save)",
                x + LABEL_DX, formTop - 12, 0xFF8888AA, false);
            else if (!rawMode) g.drawCenteredString(font,
                Component.literal("no form fields — use \"raw\""),
                x + w / 2, formTop + 12, 0xFF8888AA);
            return;
        }

        // form rows inside the clipped, scrollable viewport
        scroll.beginClip(g);
        int top = scroll.contentTop();
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = top + i * ROW_H;
            if (!scroll.rowVisible(rowTop, ROW_H)) continue;
            rows.get(i).drawLabel(g, font, x + LABEL_DX, rowTop + 4);
        }
        scroll.endClip(g);
        scroll.renderScrollbar(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (rawMode || rows.isEmpty()) return false;
        if (mx < x || mx > x + w || my < scroll.viewTop() || my > scroll.viewBottom()) {
            return false;
        }
        if (scroll.onScroll(sy)) { layoutRows(); return true; }
        return false;
    }
}
