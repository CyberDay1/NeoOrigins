package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory.FieldRow;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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

    private OriginCreatorScreen parent;
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
    public void refBackdrop(GuiGraphics g) { refPicker.renderBackdrop(g); }
    public boolean refScroll(double mx, double my, double sy) {
        return refPicker.onScroll(mx, my, sy);
    }

    /**
     * (Re)build for {@code target} in the rectangle {@code (x,y,w,h)}. Registers
     * the field widgets (or the raw box) with the screen. {@code target} may be
     * {@code null} — the panel then renders nothing editable.
     */
    public void init(OriginCreatorScreen parent, PowerDraft target, boolean rawMode,
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
        ResourceLocation type = parseType(target.typeId);
        List<FormFieldSpec> specs;
        try {
            specs = type == null ? List.of() : FormModel.forPower(type);
        } catch (RuntimeException e) {
            specs = List.of(); // unresolvable — raw toggle still covers it
        }
        int fieldW = Math.min(w - FIELD_DX - 12, 240);
        for (FormFieldSpec spec : specs) {
            FieldRow row = FieldWidgetFactory.create(spec, this::openRefPicker);
            row.build(parent, font, fieldW, 16);
            rows.add(row);
        }
        scroll.setContentHeight(rows.size() * ROW_H + 4);
    }

    /** Open the condition/action picker for {@code field}; on pick, write a
     *  {@code {"type":"<id>"}} skeleton into the power body and rebuild. */
    private void openRefPicker(String kind, String field) {
        push(); // keep edits to the other fields
        java.util.List<String> src = new ArrayList<>("action".equals(kind)
            ? com.cyberday1.neoorigins.compat.action.ActionParser.KNOWN_TYPES
            : com.cyberday1.neoorigins.compat.condition.ConditionParser.KNOWN_TYPES);
        java.util.Collections.sort(src);
        refPicker.open("pick " + kind + " type", () -> src,
            picked -> applyRef(field, picked), parent::requestRebuild);
        parent.requestRebuild();
    }

    private void applyRef(String field, String typeId) {
        if (target == null) return;
        JsonObject body = parseObject(target.rawJson);
        JsonObject ref = new JsonObject();
        ref.addProperty("type", typeId);
        body.add(field, ref);
        target.rawJson = body.toString();
    }

    /** Re-place field widgets against the scroll offset; hide off-view rows. */
    public void layout() {
        int fieldX = x + FIELD_DX;
        int top = scroll.contentTop();
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = top + i * ROW_H;
            FieldRow row = rows.get(i);
            row.reposition(fieldX, rowTop);
            row.setVisible(!rawMode && scroll.rowVisible(rowTop, ROW_H));
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

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (target == null) return;
        Font font = parent.font();

        if (rawMode) {
            g.drawString(font,
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
        int top = scroll.contentTop();
        FieldRow hovered = null;
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = top + i * ROW_H;
            if (!scroll.rowVisible(rowTop, ROW_H)) continue;
            rows.get(i).drawLabel(g, font, x + LABEL_DX, rowTop + 4);
            if (mouseX >= x && mouseX <= x + w
                    && mouseY >= rowTop && mouseY < rowTop + ROW_H) {
                hovered = rows.get(i);
            }
        }
        scroll.endClip(g);
        scroll.renderScrollbar(g);

        if (hovered != null) {
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.tooltip(
                g, font, hovered.tooltip(), mouseX, mouseY,
                parent.width, parent.height);
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
}
