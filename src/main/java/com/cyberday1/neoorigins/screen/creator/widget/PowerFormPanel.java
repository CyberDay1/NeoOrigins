package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory.FieldRow;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The reusable single-power body editor: renders one {@link PowerDraft}'s
 * config as a scrollable {@link FormModel}-driven field form, or a raw-JSON
 * box, and round-trips it through {@link PowerDraft#rawJson} (the config object
 * with no {@code type} — the serializer injects that).
 *
 * <p>Shared by the Powers tab (generic, with list-nav + type picker around it)
 * and the Appearance tab (scoped to the Tier-A visual powers). The owning tab
 * lays out its own header and hands this panel the remaining rectangle.
 */
public final class PowerFormPanel {

    private static final int ROW_H = 22, LABEL_DX = 6, FIELD_DX = 140;

    private CreatorHost parent;
    private PowerDraft target;
    private boolean rawMode;
    private int x, y, w, h;

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<FieldRow> rows = new ArrayList<>();
    private EditBox rawBox;
    private final SearchPickerOverlay refPicker = new SearchPickerOverlay();

    /** True when this panel is showing the raw-JSON escape instead of fields. */
    public boolean isRaw() { return rawMode; }

    /** True while the condition/action picker overlay owns input. */
    public boolean overlayOpen() { return refPicker.isOpen(); }
    public void refBackdrop(GuiGraphicsExtractor g) { refPicker.renderBackdrop(g); }
    public boolean refScroll(double mx, double my, double sy) {
        return refPicker.onScroll(mx, my, sy);
    }

    /**
     * (Re)build for {@code target} in the rectangle {@code (x,y,w,h)}. Registers
     * the field widgets (or the raw box) with the screen. {@code target} may be
     * {@code null} — the panel then renders nothing editable.
     */
    public void init(CreatorHost parent, PowerDraft target, boolean rawMode,
                     int x, int y, int w, int h) {
        this.parent = parent;
        this.target = target;
        this.rawMode = rawMode;
        this.x = x; this.y = y; this.w = w; this.h = h;
        rows.clear();
        rawBox = null;

        if (refPicker.isOpen()) {                 // overlay owns input
            int pw = Math.min(w - 20, 360), ph = h - 16;
            refPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        scroll.setViewport(x, y, w, h);

        if (target == null) { scroll.setContentHeight(0); return; }

        Font font = parent.font();
        if (rawMode) {
            rawBox = new EditBox(font, x + LABEL_DX, y, w - LABEL_DX - 6, h - 4,
                Component.literal("raw json"));
            rawBox.setMaxLength(32767);
            parent.register(rawBox);
            scroll.setContentHeight(0);
        } else {
            buildForm(font);
        }
        layout();
    }

    private void buildForm(Font font) {
        Identifier type = parseType(target.typeId);
        List<FormFieldSpec> specs;
        try {
            specs = type == null ? List.of() : FormModel.forPower(type);
        } catch (RuntimeException e) {
            specs = List.of(); // unresolvable — raw toggle still covers it
        }
        int fieldW = Math.min(w - FIELD_DX - 12, 240);
        // Parse the current body once so each row can seed its initial value
        // during build. Crucial for RefRow / ArrayRefRow: their sub-form / item
        // list is built from the current type / array, so the JSON must be
        // known before layout() runs.
        JsonObject body = parseObject(target.rawJson);
        Runnable rebuildCb = () -> { push(); parent.requestRebuild(); };
        for (FormFieldSpec spec : specs) {
            FieldRow row = FieldWidgetFactory.create(spec, this::openRefPicker, this::openEnumPicker,
                this::openTypePicker, rebuildCb);
            row.build(parent, font, fieldW, 16);
            row.fromJson(body.get(spec.name()));
            rows.add(row);
        }
        int total = 4;
        for (FieldRow row : rows) total += row.height();
        scroll.setContentHeight(total);
    }

    /**
     * Open the shared picker for a RefRow / ArrayRefRow type pick. The {@code sink}
     * (provided by the calling row) just mutates the row's local state; this
     * method takes care of pushing the row tree into {@code target.rawJson}
     * before the picker (so the picker's overlay sees a stable snapshot) and
     * pushing + rebuilding again after — the post-pick push captures the new
     * type, the rebuild creates a fresh row tree seeded from the new JSON.
     */
    private void openTypePicker(String kind, java.util.function.Consumer<String> sink) {
        push();
        java.util.List<String> src = new ArrayList<>("action".equals(kind)
            ? com.cyberday1.neoorigins.compat.action.ActionParser.KNOWN_TYPES
            : "condition".equals(kind)
                ? com.cyberday1.neoorigins.compat.condition.ConditionParser.KNOWN_TYPES
                : java.util.Collections.emptyList());
        java.util.Collections.sort(src);
        refPicker.open("pick " + kind, () -> src,
            sink::accept,
            () -> { push(); parent.requestRebuild(); });
        parent.requestRebuild();
    }

    /**
     * Open the shared picker showing a large ENUM field's allowed values.
     * Selection writes directly into {@code target.rawJson} and triggers a
     * rebuild so the row reflects the new value via {@link #pull()}.
     */
    private void openEnumPicker(String field, List<String> values) {
        push();
        refPicker.open("pick " + field, () -> values,
            picked -> applyString(field, picked),
            parent::requestRebuild);
        parent.requestRebuild();
    }

    /**
     * Open the picker for {@code field}. condition/action insert a
     * {@code {"type":"<id>"}} skeleton; a registry kind (particle, item, …)
     * inserts the plain id string. Then rebuild so the form reflects it.
     */
    private void openRefPicker(String kind, String field) {
        push(); // keep edits to the other fields
        boolean dsl = "condition".equals(kind) || "action".equals(kind);
        java.util.List<String> src;
        if (dsl) {
            src = new ArrayList<>("action".equals(kind)
                ? com.cyberday1.neoorigins.compat.action.ActionParser.KNOWN_TYPES
                : com.cyberday1.neoorigins.compat.condition.ConditionParser.KNOWN_TYPES);
            java.util.Collections.sort(src);
        } else {
            src = com.cyberday1.neoorigins.screen.creator.CreatorAssets
                .registryList(kind); // already sorted
        }
        refPicker.open("pick " + kind, () -> src,
            picked -> { if (dsl) applyRef(field, picked); else applyString(field, picked); },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void applyString(String field, String value) {
        if (target == null) return;
        JsonObject body = parseObject(target.rawJson);
        body.addProperty(field, value);
        target.rawJson = body.toString();
    }

    private void applyRef(String field, String typeId) {
        if (target == null) return;
        JsonObject body = parseObject(target.rawJson);
        JsonObject ref = new JsonObject();
        ref.addProperty("type", typeId);
        body.add(field, ref);
        target.rawJson = body.toString();
    }

    /** Re-place field widgets against the scroll offset; hide off-view rows.
     *  Variable-height aware — RefRow expands by its sub-form's row count. */
    public void layout() {
        int fieldX = x + FIELD_DX;
        int rowTop = scroll.contentTop();
        for (FieldRow row : rows) {
            int rh = row.height();
            row.reposition(fieldX, rowTop);
            row.setVisible(!rawMode && scroll.rowVisible(rowTop, rh));
            rowTop += rh;
        }
    }

    public void pull() {
        if (target == null) return;
        if (rawMode) {
            if (rawBox != null) rawBox.setValue(target.rawJson == null ? "{}" : target.rawJson);
            return;
        }
        JsonObject body = parseObject(target.rawJson);
        for (FieldRow row : rows) row.fromJson(body.get(row.fieldName()));
    }

    public void push() {
        if (target == null) return;
        if (rawMode) {
            if (rawBox != null) target.rawJson = rawBox.getValue();
            return;
        }
        JsonObject body = new JsonObject();
        for (FieldRow row : rows) {
            JsonElement v = row.toJson();
            if (v != null) body.add(row.fieldName(), v);
        }
        target.rawJson = body.toString();
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (target == null) return;
        Font font = parent.font();

        if (rawMode) {
            g.text(font,
                "Editing this power's config as JSON — the \"type\" is added for you on save.",
                x + LABEL_DX, y - 10,
                com.cyberday1.neoorigins.screen.creator.CreatorStyle.TEXT_DIM, false);
            return;
        }
        if (rows.isEmpty()) {
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.emptyState(g, font,
                "This power has no editable fields — switch to JSON to configure it.",
                x + w / 2, y + 12);
            return;
        }

        scroll.beginClip(g);
        int rowTop = scroll.contentTop();
        FieldRow hovered = null;
        for (FieldRow row : rows) {
            int rh = row.height();
            if (scroll.rowVisible(rowTop, rh)) {
                row.drawLabel(g, font, x + LABEL_DX, rowTop + 4);
                int headerH = Math.min(rh, FieldWidgetFactory.DEFAULT_ROW_H);
                if (mouseX >= x && mouseX <= x + w
                        && mouseY >= rowTop && mouseY < rowTop + headerH) {
                    hovered = row;
                }
            }
            rowTop += rh;
        }
        scroll.endClip(g);
        scroll.renderScrollbar(g);

        if (hovered != null) {
            parent.queueTooltip(hovered.tooltip(), mouseX, mouseY);
        }
    }

    public boolean onScroll(double mx, double my, double sy) {
        if (rawMode || rows.isEmpty()) return false;
        if (mx < x || mx > x + w || my < scroll.viewTop() || my > scroll.viewBottom()) {
            return false;
        }
        if (scroll.onScroll(sy)) { layout(); return true; }
        return false;
    }

    private static Identifier parseType(String s) {
        try { return Identifier.parse(s); }
        catch (RuntimeException e) { return null; }
    }

    private static JsonObject parseObject(String s) {
        try {
            JsonElement el = JsonParser.parseString(s == null || s.isBlank() ? "{}" : s);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) { return new JsonObject(); }
    }
}
