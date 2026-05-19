package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.screen.creator.OriginCreatorScreen;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

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

    /** One labelled, value-bearing row in the form. */
    public interface FieldRow {
        String fieldName();
        /** Create widgets (registered with the screen) at a provisional origin. */
        void build(OriginCreatorScreen parent, Font font, int fieldW, int h);
        /** Move widgets so the row sits at content-Y {@code y}; field column at {@code fieldX}. */
        void reposition(int fieldX, int y);
        void setVisible(boolean v);
        void drawLabel(GuiGraphicsExtractor g, Font font, int labelX, int y);
        /** Current value as JSON, or {@code null} to omit the field entirely. */
        JsonElement toJson();
        void fromJson(JsonElement el);
    }

    public static FieldRow create(FormFieldSpec spec) {
        return switch (spec.kind()) {
            case BOOLEAN -> new BoolRow(spec);
            case ENUM    -> new EnumRow(spec);
            case INTEGER, NUMBER -> new NumericRow(spec);
            case STRING  -> new TextRow(spec, false);
            // ARRAY/OBJECT/REF/MIXED/UNKNOWN → raw-JSON escape
            default      -> new TextRow(spec, true);
        };
    }

    // ── shared base ─────────────────────────────────────────────────────────

    private abstract static class Base implements FieldRow {
        final FormFieldSpec spec;
        Base(FormFieldSpec spec) { this.spec = spec; }
        @Override public String fieldName() { return spec.name(); }
        @Override public void drawLabel(GuiGraphicsExtractor g, Font font, int labelX, int y) {
            int color = spec.required() ? 0xFFE8E8F0 : 0xFFBBBBCC;
            g.text(font, spec.name(), labelX, y, color, false);
        }
    }

    // ── text / raw-JSON ─────────────────────────────────────────────────────

    private static final class TextRow extends Base {
        private final boolean rawJson;
        private EditBox box;
        TextRow(FormFieldSpec spec, boolean rawJson) { super(spec); this.rawJson = rawJson; }

        @Override public void build(OriginCreatorScreen parent, Font font, int fieldW, int h) {
            box = new EditBox(font, 0, 0, fieldW, h, Component.literal(spec.name()));
            box.setMaxLength(32767);
            if (spec.defaultValue() != null && !rawJson) box.setValue(String.valueOf(spec.defaultValue()));
            parent.register(box);
        }
        @Override public void reposition(int fieldX, int y) { box.setPosition(fieldX, y); }
        @Override public void setVisible(boolean v) { box.visible = v; box.active = v; }

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
        @Override public void build(OriginCreatorScreen parent, Font font, int fieldW, int h) {
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
        @Override public void build(OriginCreatorScreen parent, Font font, int fieldW, int h) {
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

        @Override public void build(OriginCreatorScreen parent, Font font, int fieldW, int h) {
            this.fieldW = fieldW; this.rowH = h;
            int toggleW = 30, gap = 4, boxW = Math.max(40, fieldW - toggleW - gap);
            box = new EditBox(font, 0, 0, boxW, h, Component.literal(spec.name()));
            box.setFilter(this::accept);
            if (spec.defaultValue() != null) box.setValue(String.valueOf(spec.defaultValue()));
            modeToggle = Button.builder(modeLabel(), b -> {
                random = !random;
                box.setValue("");
                modeToggle.setMessage(modeLabel());
            }).bounds(0, 0, toggleW, h).build();
            parent.register(box);
            parent.register(modeToggle);
        }

        private Component modeLabel() { return Component.literal(random ? "rnd" : "n"); }

        /** Permit digits / sign / dot, plus a single comma in random (min,max) mode. */
        private boolean accept(String s) {
            if (s.isEmpty()) return true;
            String num = integral ? "-?\\d*" : "-?\\d*\\.?\\d*";
            return random ? s.matches(num + ",?" + num) : s.matches(num);
        }

        @Override public void reposition(int fieldX, int y) {
            box.setPosition(fieldX, y);
            modeToggle.setPosition(fieldX + fieldW - 30, y);
        }
        @Override public void setVisible(boolean v) {
            box.visible = v; box.active = v;
            modeToggle.visible = v; modeToggle.active = v;
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
