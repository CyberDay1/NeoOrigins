package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link FormFieldSpec} into one editable {@link FieldRow} for the
 * Powers tab. One concrete widget per {@link FormFieldSpec.Kind}; ARRAY /
 * OBJECT / REF / MIXED / UNKNOWN fall to a single-line raw-JSON box (no
 * recursive sub-forms in v1 — the per-power raw-JSON escape hatch covers
 * deep edits).
 *
 * <p><b>Numeric randomize affordance (Phase-5 forward design):</b>
 * {@link NumericRow} models its value as <em>either</em> a number <em>or</em>
 * the {@code {"random":{"min":a,"max":b}}} object the planned {@code random(min,max)}
 * DSL will consume, and ships a mode toggle from day one. v1's randomize input
 * is a {@code min,max} pair typed into the same box; Phase 5 only needs to swap
 * that for a richer control — the JSON round-trips losslessly either way, so
 * no retrofit of the value model is required.
 */
public final class FieldWidgetFactory {

    private FieldWidgetFactory() {}

    /** Default height (in pixels) consumed by a single-line FieldRow. */
    public static final int DEFAULT_ROW_H = 22;

    /** One labelled, value-bearing row in the form. */
    public interface FieldRow {
        String fieldName();
        /** Create widgets (registered with the screen) at a provisional origin. */
        void build(CreatorHost parent, Font font, int fieldW, int h);
        /** Move widgets so the row sits at content-Y {@code y}; field column at {@code fieldX}. */
        void reposition(int fieldX, int y);
        void setVisible(boolean v);
        void drawLabel(GuiGraphics g, Font font, int labelX, int y);
        /** Current value as JSON, or {@code null} to omit the field entirely. */
        JsonElement toJson();
        void fromJson(JsonElement el);
        /** Hover help: name/required, schema description, type/range/values. */
        default List<String> tooltip() { return List.of(); }
        /** Vertical space (px) the row consumes; multi-row widgets override. */
        default int height() { return DEFAULT_ROW_H; }
    }

    /**
     * Lets a REF field open a searchable picker over the condition/action
     * vocabulary. {@code sourceKind} is {@code "condition"} or {@code "action"};
     * {@code field} is the JSON key being filled.
     */
    public interface RefOpener { void open(String sourceKind, String field); }

    /**
     * Lets a large-enum field open a searchable picker over its allowed values
     * instead of cycling through them. Used for any ENUM with more than
     * {@link #ENUM_PICKER_THRESHOLD} options (e.g. {@code action_on_event.event},
     * which has ~30 keys — cycling through them is painful).
     */
    public interface EnumOpener { void open(String field, List<String> values); }

    /** ENUMs with more options than this open a search picker instead of a cycle. */
    public static final int ENUM_PICKER_THRESHOLD = 6;

    public static FieldRow create(FormFieldSpec spec) { return create(spec, null, null); }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener) {
        return create(spec, refOpener, null);
    }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener, EnumOpener enumOpener) {
        return switch (spec.kind()) {
            case BOOLEAN -> new BoolRow(spec);
            case ENUM    -> (enumOpener != null && spec.enumValues().size() > ENUM_PICKER_THRESHOLD)
                                ? new EnumPickerRow(spec, enumOpener)
                                : new EnumRow(spec);
            case INTEGER, NUMBER -> new NumericRow(spec);
            case STRING  -> new TextRow(spec, false, refOpener);
            case REF     -> (refOpener != null && pickKind(spec) != null)
                                ? new RefRow(spec, refOpener, enumOpener)
                                : new TextRow(spec, true, refOpener);
            // ARRAY/OBJECT/MIXED/UNKNOWN → raw-JSON escape
            default      -> new TextRow(spec, true, null);
        };
    }

    /**
     * What a row's "pick" button browses, or null if none:
     * REF → "condition"/"action"; STRING → a registry kind (particle, item, …).
     */
    private static String pickKind(FormFieldSpec spec) {
        if (spec.kind() == FormFieldSpec.Kind.REF) {
            String hay = ((spec.ref() == null ? "" : spec.ref()) + " " + spec.name())
                .toLowerCase(java.util.Locale.ROOT);
            if (hay.contains("action")) return "action";
            if (hay.contains("condition")) return "condition";
            return null;
        }
        if (spec.kind() == FormFieldSpec.Kind.STRING) {
            return com.cyberday1.neoorigins.screen.creator.CreatorAssets
                .registryKind(spec.name());
        }
        return null;
    }

    // ── shared base ─────────────────────────────────────────────────────────

    private abstract static class Base implements FieldRow {
        final FormFieldSpec spec;
        Base(FormFieldSpec spec) { this.spec = spec; }
        @Override public String fieldName() { return spec.name(); }
        @Override public void drawLabel(GuiGraphics g, Font font, int labelX, int y) {
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.label(
                g, font, spec.name(), labelX, y, spec.required());
        }
        @Override public List<String> tooltip() {
            List<String> t = new java.util.ArrayList<>();
            t.add(com.cyberday1.neoorigins.screen.creator.CreatorStyle.title(spec.name())
                + (spec.required() ? "  (required)" : "  (optional)"));
            if (spec.description() != null && !spec.description().isBlank()) {
                t.add(spec.description());
            } else {
                String doc = com.cyberday1.neoorigins.screen.creator.CreatorAssets
                    .DOC.get(spec.name());
                if (doc != null) t.add(doc);
            }
            String kind = switch (spec.kind()) {
                case STRING  -> "text";
                case INTEGER -> "whole number";
                case NUMBER  -> "decimal number";
                case BOOLEAN -> "true / false";
                case ENUM    -> "pick one";
                case ARRAY   -> "list (JSON)";
                case OBJECT  -> "object (JSON)";
                case REF     -> "DSL reference (JSON)";
                case MIXED   -> "value or object (JSON)";
                case UNKNOWN -> "JSON";
            };
            StringBuilder meta = new StringBuilder("type: ").append(kind);
            if (spec.hasRange()) {
                meta.append("   range ").append(fmt(spec.min()))
                    .append(" .. ").append(fmt(spec.max()));
            }
            if (spec.defaultValue() != null) {
                meta.append("   default ").append(spec.defaultValue());
            }
            t.add(meta.toString());
            if (!spec.enumValues().isEmpty()) {
                List<String> vals = spec.enumValues();
                // For small enums (cycle-button-sized), show the full list.
                // For larger ones, cap so the tooltip doesn't run off-screen —
                // the search picker shows the rest interactively.
                if (vals.size() <= ENUM_PICKER_THRESHOLD) {
                    t.add("one of: " + String.join(", ", vals));
                } else {
                    t.add("one of " + vals.size() + ": "
                        + String.join(", ", vals.subList(0, 5))
                        + ", … (click field to pick)");
                }
            }
            if (spec.ref() != null) t.add("references: " + spec.ref());
            switch (spec.kind()) {
                case REF, OBJECT, ARRAY, MIXED, UNKNOWN ->
                    t.add("No guided sub-form yet — edit this as JSON.");
                default -> { }
            }
            return t;
        }
        private static String fmt(Double d) {
            if (d == null) return "?";
            return d == Math.rint(d) ? Long.toString(d.longValue()) : d.toString();
        }
    }

    // ── text / raw-JSON ─────────────────────────────────────────────────────

    private static final class TextRow extends Base {
        private final boolean rawJson;
        private final RefOpener refOpener;
        private final String refKind; // non-null → show a "pick" button
        private EditBox box;
        private Button pick;
        TextRow(FormFieldSpec spec, boolean rawJson, RefOpener refOpener) {
            super(spec);
            this.rawJson = rawJson;
            this.refOpener = refOpener;
            this.refKind = refOpener != null ? pickKind(spec) : null;
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            int boxW = refKind != null ? Math.max(40, fieldW - 42) : fieldW;
            box = new EditBox(font, 0, 0, boxW, h, Component.literal(spec.name()));
            box.setMaxLength(32767);
            if (spec.defaultValue() != null && !rawJson) box.setValue(String.valueOf(spec.defaultValue()));
            parent.register(box);
            if (refKind != null) {
                pick = Button.builder(Component.literal("pick"),
                        b -> refOpener.open(refKind, spec.name()))
                    .bounds(0, 0, 38, h).build();
                parent.register(pick);
            }
        }
        @Override public void reposition(int fieldX, int y) {
            box.setPosition(fieldX, y);
            if (pick != null) pick.setPosition(fieldX + box.getWidth() + 4, y);
        }
        @Override public void setVisible(boolean v) {
            box.visible = v; box.active = v;
            if (pick != null) { pick.visible = v; pick.active = v; }
        }

        @Override public JsonElement toJson() {
            String s = box.getValue().trim();
            if (s.isEmpty()) return null;
            if (rawJson) {
                try { return JsonParser.parseString(s); }
                catch (RuntimeException e) { return new JsonPrimitive(s); }
            }
            return new JsonPrimitive(s);
        }
        @Override public void fromJson(JsonElement el) {
            if (el == null || el.isJsonNull()) { box.setValue(""); return; }
            box.setValue(el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
                ? el.getAsString() : el.toString());
        }
    }

    // ── boolean ─────────────────────────────────────────────────────────────

    private static final class BoolRow extends Base {
        private boolean value;
        private Button button;
        BoolRow(FormFieldSpec spec) {
            super(spec);
            value = Boolean.TRUE.equals(spec.defaultValue());
        }
        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            button = Button.builder(label(), b -> { value = !value; button.setMessage(label()); })
                .bounds(0, 0, Math.min(fieldW, 70), h).build();
            parent.register(button);
        }
        private Component label() { return Component.literal(Boolean.toString(value)); }
        @Override public void reposition(int fieldX, int y) { button.setPosition(fieldX, y); }
        @Override public void setVisible(boolean v) { button.visible = v; button.active = v; }
        @Override public JsonElement toJson() { return new JsonPrimitive(value); }
        @Override public void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) value = el.getAsBoolean();
            if (button != null) button.setMessage(label());
        }
    }

    // ── enum (cycler) ───────────────────────────────────────────────────────

    private static final class EnumRow extends Base {
        private final List<String> values;
        private int idx;
        private Button button;
        EnumRow(FormFieldSpec spec) {
            super(spec);
            values = spec.enumValues().isEmpty() ? List.of("") : spec.enumValues();
            if (spec.defaultValue() != null) {
                int i = values.indexOf(String.valueOf(spec.defaultValue()));
                if (i >= 0) idx = i;
            }
        }
        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            button = Button.builder(label(), b -> {
                idx = (idx + 1) % values.size(); button.setMessage(label());
            }).bounds(0, 0, fieldW, h).build();
            parent.register(button);
        }
        private Component label() { return Component.literal(values.get(idx)); }
        @Override public void reposition(int fieldX, int y) { button.setPosition(fieldX, y); }
        @Override public void setVisible(boolean v) { button.visible = v; button.active = v; }
        @Override public JsonElement toJson() {
            String v = values.get(idx);
            return v.isEmpty() ? null : new JsonPrimitive(v);
        }
        @Override public void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) {
                int i = values.indexOf(el.getAsString());
                if (i >= 0) idx = i;
            }
            if (button != null) button.setMessage(label());
        }
    }

    // ── enum (search picker — for large enums) ──────────────────────────────

    private static final class EnumPickerRow extends Base {
        private final List<String> values;
        private final EnumOpener opener;
        private String current = "";
        private Button button;

        EnumPickerRow(FormFieldSpec spec, EnumOpener opener) {
            super(spec);
            // Drop case-insensitive duplicates while preserving order; prefer
            // the lowercase variant when both exist (runtime is case-insensitive
            // for events, equipment slots, etc.). Trims the ~62-entry event
            // enum down to ~30 actual choices.
            java.util.Set<String> seenLower = new java.util.HashSet<>();
            List<String> dedup = new ArrayList<>(spec.enumValues().size());
            // First pass: take the lowercase form when present.
            java.util.Set<String> lowered = new java.util.HashSet<>();
            for (String v : spec.enumValues()) {
                lowered.add(v.toLowerCase(java.util.Locale.ROOT));
            }
            for (String v : spec.enumValues()) {
                String lc = v.toLowerCase(java.util.Locale.ROOT);
                if (!seenLower.add(lc)) continue;
                dedup.add(lowered.contains(lc) ? lc : v);
            }
            this.values = java.util.Collections.unmodifiableList(dedup);
            this.opener = opener;
            if (spec.defaultValue() != null) {
                String d = String.valueOf(spec.defaultValue()).toLowerCase(java.util.Locale.ROOT);
                if (values.contains(d)) current = d;
            }
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            button = Button.builder(label(), b -> opener.open(spec.name(), values))
                .bounds(0, 0, fieldW, h).build();
            parent.register(button);
        }
        private Component label() {
            return Component.literal(current.isEmpty() ? "(click to pick)" : current);
        }
        @Override public void reposition(int fieldX, int y) { button.setPosition(fieldX, y); }
        @Override public void setVisible(boolean v) { button.visible = v; button.active = v; }
        @Override public JsonElement toJson() {
            return current.isEmpty() ? null : new JsonPrimitive(current);
        }
        @Override public void fromJson(JsonElement el) {
            current = (el != null && el.isJsonPrimitive()) ? el.getAsString() : "";
            if (button != null) button.setMessage(label());
        }
    }

    // ── REF (entity_action / condition with inline sub-form) ────────────────

    /**
     * Renders a REF field — entity_action / condition / etc. — as a type
     * picker plus an inline sub-form of the picked type's fields, indented
     * underneath. The type picker reuses {@code refPicker} via the
     * {@link RefOpener} callback (same path TextRow's "pick" button uses);
     * after selection, {@code PowerFormPanel.applyRef} writes
     * {@code {"type":"<id>"}} to {@code target.rawJson} and requests a
     * rebuild, which re-instantiates this RefRow with the new type.
     *
     * <p>Sub-rows are built by {@link com.cyberday1.neoorigins.power.schemaform.FormModel#forAction}
     * / {@link com.cyberday1.neoorigins.power.schemaform.FormModel#forCondition}.
     * Their REF/ENUM widgets receive {@code null} openers so nested REFs
     * fall back to {@link TextRow} (raw-JSON) — Phase C extends this with
     * proper JSON-path tracking so nested pickers route writes to the right
     * sub-object instead of the top level.
     */
    private static final class RefRow extends Base {
        private static final int HEADER_H = 22;
        private static final int INDENT = 12;
        private static final String EMPTY_LABEL = "(pick %s)";

        private final RefOpener refOpener;
        private final EnumOpener enumOpener;
        private final String refKind; // "action" or "condition" or null

        private CreatorHost parent;
        private Font font;
        private int fieldW;
        private Button typeButton;
        private Button clearButton;
        private String currentType = "";
        private final List<FieldRow> subRows = new ArrayList<>();
        /** Per-sub-row y offset inside this RefRow (relative to header top). */
        private final List<Integer> subYs = new ArrayList<>();
        private int lastReposY;

        RefRow(FormFieldSpec spec, RefOpener refOpener, EnumOpener enumOpener) {
            super(spec);
            this.refOpener = refOpener;
            this.enumOpener = enumOpener;
            this.refKind = pickKind(spec);
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            typeButton = Button.builder(typeLabel(),
                    b -> { if (refOpener != null && refKind != null)
                                refOpener.open(refKind, spec.name()); })
                .bounds(0, 0, Math.max(40, fieldW - 26), h).build();
            parent.register(typeButton);
            clearButton = Button.builder(Component.literal("x"),
                    b -> { currentType = ""; subRows.clear(); parent.requestRebuild(); })
                .bounds(0, 0, 22, h).build();
            parent.register(clearButton);
            buildSubRows();
        }

        private void buildSubRows() {
            subRows.clear();
            if (currentType.isEmpty() || refKind == null) return;
            List<FormFieldSpec> specs = "action".equals(refKind)
                ? com.cyberday1.neoorigins.power.schemaform.FormModel.forAction(currentType)
                : "condition".equals(refKind)
                    ? com.cyberday1.neoorigins.power.schemaform.FormModel.forCondition(currentType)
                    : List.of();
            int subW = Math.max(40, fieldW - INDENT);
            for (FormFieldSpec sub : specs) {
                // Phase B: pass null openers so nested REFs fall back to TextRow
                // raw-JSON. Phase C extends with JSON-path-aware sub-openers.
                FieldRow row = FieldWidgetFactory.create(sub, null, null);
                row.build(parent, font, subW, 16);
                subRows.add(row);
            }
        }

        @Override public int height() {
            int h = HEADER_H;
            for (FieldRow sub : subRows) h += sub.height();
            return h;
        }

        @Override public void reposition(int fieldX, int y) {
            lastReposY = y;
            typeButton.setPosition(fieldX, y);
            clearButton.setPosition(fieldX + fieldW - 22, y);
            subYs.clear();
            int subY = y + HEADER_H;
            for (FieldRow sub : subRows) {
                subYs.add(subY);
                sub.reposition(fieldX + INDENT, subY);
                subY += sub.height();
            }
        }

        @Override public void setVisible(boolean v) {
            typeButton.visible = v; typeButton.active = v;
            clearButton.visible = v; clearButton.active = v;
            for (FieldRow sub : subRows) sub.setVisible(v);
        }

        @Override public void drawLabel(GuiGraphics g, Font font, int labelX, int y) {
            super.drawLabel(g, font, labelX, y);
            // Sub-row labels indented by INDENT under the type-picker header.
            for (int i = 0; i < subRows.size() && i < subYs.size(); i++) {
                subRows.get(i).drawLabel(g, font, labelX + INDENT, subYs.get(i));
            }
        }

        @Override public JsonElement toJson() {
            if (currentType.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("type", currentType);
            for (FieldRow sub : subRows) {
                JsonElement v = sub.toJson();
                if (v != null) body.add(sub.fieldName(), v);
            }
            return body;
        }

        @Override public void fromJson(JsonElement el) {
            String newType = "";
            if (el != null && el.isJsonObject()) {
                JsonObject body = el.getAsJsonObject();
                if (body.has("type") && body.get("type").isJsonPrimitive()) {
                    newType = body.get("type").getAsString();
                }
            }
            boolean typeChanged = !newType.equals(currentType);
            currentType = newType;
            if (typeChanged && parent != null) {
                buildSubRows();
            }
            if (typeButton != null) typeButton.setMessage(typeLabel());
            if (el != null && el.isJsonObject()) {
                JsonObject body = el.getAsJsonObject();
                for (FieldRow sub : subRows) sub.fromJson(body.get(sub.fieldName()));
            }
        }

        private Component typeLabel() {
            if (currentType.isEmpty()) {
                return Component.literal(String.format(EMPTY_LABEL,
                    refKind == null ? "type" : refKind));
            }
            return Component.literal(currentType);
        }
    }

    // ── numeric (with Phase-5 randomize toggle) ─────────────────────────────

    private static final class NumericRow extends Base {
        private final boolean integral;
        private boolean random;          // false = scalar, true = {"random":{min,max}}
        private EditBox box;             // scalar value, or "min,max" when random
        private Button modeToggle;       // n ⇄ rnd
        private int fieldW, rowH;

        NumericRow(FormFieldSpec spec) {
            super(spec);
            integral = spec.kind() == FormFieldSpec.Kind.INTEGER;
        }

        private static final int TOGGLE_W = 52;

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.fieldW = fieldW; this.rowH = h;
            int gap = 4, boxW = Math.max(40, fieldW - TOGGLE_W - gap);
            box = new EditBox(font, 0, 0, boxW, h, Component.literal(spec.name()));
            box.setFilter(this::accept);
            if (spec.defaultValue() != null) box.setValue(String.valueOf(spec.defaultValue()));
            applyHint();
            modeToggle = Button.builder(modeLabel(), b -> {
                random = !random;
                box.setValue("");
                applyHint();
                modeToggle.setMessage(modeLabel());
            }).bounds(0, 0, TOGGLE_W, h).build();
            parent.register(box);
            parent.register(modeToggle);
        }

        /** Placeholder makes the current mode obvious in the (empty) box. */
        private void applyHint() {
            box.setHint(Component.literal(random ? "min, max"
                : (integral ? "whole number" : "number")));
        }

        private Component modeLabel() { return Component.literal(random ? "random" : "fixed"); }

        /** Permit digits / sign / dot, plus a single comma in random (min,max) mode. */
        private boolean accept(String s) {
            if (s.isEmpty()) return true;
            String num = integral ? "-?\\d*" : "-?\\d*\\.?\\d*";
            return random ? s.matches(num + ",?" + num) : s.matches(num);
        }

        @Override public void reposition(int fieldX, int y) {
            box.setPosition(fieldX, y);
            modeToggle.setPosition(fieldX + fieldW - TOGGLE_W, y);
        }
        @Override public void setVisible(boolean v) {
            box.visible = v; box.active = v;
            modeToggle.visible = v; modeToggle.active = v;
        }
        @Override public List<String> tooltip() {
            List<String> t = super.tooltip();
            t.add(random
                ? "Random mode: enter \"min, max\" — rolled once per player."
                : "Click \"fixed\" → \"random\" to use a {min,max} range instead.");
            return t;
        }

        private Number parse(String s) {
            try {
                return integral ? (Number) Long.valueOf(Long.parseLong(s.trim()))
                                 : (Number) Double.valueOf(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) { return null; }
        }

        @Override public JsonElement toJson() {
            String raw = box.getValue().trim();
            if (raw.isEmpty()) return null;
            if (random) {
                String[] parts = raw.split(",", 2);
                if (parts.length != 2) return null;
                Number lo = parse(parts[0]), hi = parse(parts[1]);
                if (lo == null || hi == null) return null;
                JsonObject inner = new JsonObject();
                inner.addProperty("min", lo);
                inner.addProperty("max", hi);
                JsonObject wrap = new JsonObject();
                wrap.add("random", inner);
                return wrap;
            }
            Number n = parse(raw);
            return n == null ? null : new JsonPrimitive(n);
        }

        @Override public void fromJson(JsonElement el) {
            if (el == null || el.isJsonNull()) { random = false; box.setValue(""); }
            else if (el.isJsonObject() && el.getAsJsonObject().has("random")) {
                random = true;
                JsonObject r = el.getAsJsonObject().getAsJsonObject("random");
                box.setValue(r.get("min").getAsString() + "," + r.get("max").getAsString());
            } else {
                random = false;
                box.setValue(el.isJsonPrimitive() ? el.getAsString() : "");
            }
            if (modeToggle != null) modeToggle.setMessage(modeLabel());
        }
    }
}
