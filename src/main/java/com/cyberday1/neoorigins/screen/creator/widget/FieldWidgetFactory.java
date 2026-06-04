package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.google.gson.JsonArray;
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
        /**
         * Deepest row whose header band contains the cursor, given this row was
         * drawn at {@code (labelX, y)} with its field column ending at
         * {@code fieldRight}. Leaf rows test their own header; container rows
         * (object / array-of-object) override to recurse into children first so
         * a nested field's tooltip wins over its parent's. Returns {@code null}
         * when the cursor is outside this row's subtree.
         */
        default FieldRow hoveredRow(int labelX, int y, int fieldRight, int mouseX, int mouseY) {
            if (mouseX >= labelX && mouseX <= fieldRight
                    && mouseY >= y && mouseY < y + Math.min(height(), DEFAULT_ROW_H)) {
                return this;
            }
            return null;
        }
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

    /**
     * Sink-based type picker for RefRow / ArrayRefRow. The caller provides the
     * kind (action / condition) and a callback that runs with the picked id
     * once the user selects from the picker overlay. Whoever implements this
     * is responsible for committing the form state and triggering a rebuild
     * so the new picked type's sub-form is created from scratch.
     *
     * <p>Decouples nested REF pickers from a JSON-path scheme — the row's own
     * sink mutates ITS state, and a single tree-wide rebuild downstream re-
     * serialises everything via {@code toJson()}, which is already recursive.
     */
    public interface TypePicker { void open(String kind, java.util.function.Consumer<String> sink); }

    /**
     * Sink-based registry picker for fields nested inside a sub-form (an item
     * stack's {@code item}, an effect's {@code effect}, …). Unlike the top-level
     * {@link RefOpener} — which writes the picked id straight into the power's
     * root JSON by field name and so can't address a sub-path — this just hands
     * the picked id back to the row via {@code sink}; the row sets its own widget
     * value and a downstream {@code push()+rebuild} serialises the whole tree.
     * That's how nested rows get a working "pick" button without JSON-path
     * threading (same trick {@link TypePicker} uses for nested REFs).
     */
    public interface RegistryPick { void open(String registryKind, java.util.function.Consumer<String> sink); }

    /** ENUMs with more options than this open a search picker instead of a cycle. */
    public static final int ENUM_PICKER_THRESHOLD = 6;

    public static FieldRow create(FormFieldSpec spec) { return create(spec, null, null, null, null); }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener) {
        return create(spec, refOpener, null, null, null);
    }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener, EnumOpener enumOpener) {
        return create(spec, refOpener, enumOpener, null, null);
    }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener, EnumOpener enumOpener,
                                  TypePicker typePicker, Runnable rebuildCb) {
        return create(spec, refOpener, enumOpener, typePicker, rebuildCb, null);
    }

    public static FieldRow create(FormFieldSpec spec, RefOpener refOpener, EnumOpener enumOpener,
                                  TypePicker typePicker, Runnable rebuildCb, RegistryPick registryPick) {
        return switch (spec.kind()) {
            case BOOLEAN -> new BoolRow(spec);
            case ENUM    -> (enumOpener != null && spec.enumValues().size() > ENUM_PICKER_THRESHOLD)
                                ? new EnumPickerRow(spec, enumOpener)
                                : new EnumRow(spec);
            case INTEGER, NUMBER -> new NumericRow(spec);
            case STRING  -> new TextRow(spec, false, refOpener, registryPick);
            case REF     -> (typePicker != null && rebuildCb != null && refTypeKind(spec) != null)
                                ? new RefRow(spec, typePicker, rebuildCb, registryPick)
                                : new TextRow(spec, true, refOpener, registryPick);
            case ARRAY   -> {
                if (typePicker != null && rebuildCb != null
                        && spec.itemsRef() != null && refTypeKind(spec) != null) {
                    yield new ArrayRefRow(spec, typePicker, rebuildCb, registryPick);
                } else if (rebuildCb != null && isScalarStringList(spec)) {
                    yield new ArrayStringRow(spec, rebuildCb);
                } else if (rebuildCb != null && !spec.children().isEmpty()) {
                    // Array of fixed-shape objects (starting_equipment.stacks,
                    // enchantments) → structured +/- list of inline sub-forms.
                    yield new ArrayObjectRow(spec, typePicker, rebuildCb, registryPick);
                } else {
                    yield new TextRow(spec, true, null, registryPick);
                }
            }
            // An OBJECT with a fixed child set → inline sub-form; free-form
            // objects (no children) keep the raw-JSON escape.
            case OBJECT  -> spec.children().isEmpty()
                                ? new TextRow(spec, true, null, registryPick)
                                : new ObjectRow(spec, typePicker, rebuildCb, registryPick);
            // MIXED/UNKNOWN → raw-JSON escape
            default      -> new TextRow(spec, true, null, registryPick);
        };
    }

    /** "action" / "condition" / null based on a REF's $ref or an ARRAY's items.$ref. */
    private static String refTypeKind(FormFieldSpec spec) {
        String hay = null;
        if (spec.kind() == FormFieldSpec.Kind.REF && spec.ref() != null) {
            hay = (spec.ref() + " " + spec.name()).toLowerCase(java.util.Locale.ROOT);
        } else if (spec.kind() == FormFieldSpec.Kind.ARRAY && spec.itemsRef() != null) {
            hay = (spec.itemsRef() + " " + spec.name()).toLowerCase(java.util.Locale.ROOT);
        }
        if (hay == null) return null;
        // "block_condition" / "item_condition" CONTAIN "condition" and
        // "item_action" CONTAINS "action" — the specific docs must be checked
        // before the generic action/condition, else they mis-route.
        if (hay.contains("block_condition")) return "block_condition";
        if (hay.contains("item_condition")) return "item_condition";
        if (hay.contains("item_action")) return "item_action";
        if (hay.contains("action")) return "action";
        if (hay.contains("condition")) return "condition";
        return null;
    }

    /** True for an array of scalar strings ({@code items.pattern}, no {@code items.$ref}) — a list-of-text-inputs. */
    private static boolean isScalarStringList(FormFieldSpec spec) {
        return spec.kind() == FormFieldSpec.Kind.ARRAY
            && spec.itemsRef() == null && spec.itemPattern() != null;
    }

    /**
     * What a row's "pick" button browses, or null if none:
     * REF → "block_condition"/"item_condition"/"condition"/"action"; STRING → a
     * registry kind (particle, item, …).
     */
    private static String pickKind(FormFieldSpec spec) {
        if (spec.kind() == FormFieldSpec.Kind.REF) {
            String hay = ((spec.ref() == null ? "" : spec.ref()) + " " + spec.name())
                .toLowerCase(java.util.Locale.ROOT);
            if (hay.contains("block_condition")) return "block_condition";
            if (hay.contains("item_condition")) return "item_condition";
            if (hay.contains("item_action")) return "item_action";
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
                case ARRAY   -> isScalarStringList(spec) ? "list of text" : "list (JSON)";
                case OBJECT  -> spec.children().isEmpty() ? "object (JSON)" : "object (sub-form)";
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
                case REF, MIXED, UNKNOWN ->
                    t.add("No guided sub-form yet — edit this as JSON.");
                case ARRAY -> {
                    if (isScalarStringList(spec)) {
                        t.add("List of text entries — one per row; modded/datapack ids welcome.");
                    } else if (spec.itemsRef() == null) {
                        t.add("No guided sub-form yet — edit this as JSON.");
                    }
                }
                case OBJECT -> {
                    if (spec.children().isEmpty()) t.add("No guided sub-form yet — edit this as JSON.");
                }
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
        private final RegistryPick registryPick;
        private final String refKind; // non-null → root-write "pick" button (top level)
        private final String regKind; // non-null → sink "pick" button (nested sub-form)
        private EditBox box;
        private Button pick;
        TextRow(FormFieldSpec spec, boolean rawJson, RefOpener refOpener, RegistryPick registryPick) {
            super(spec);
            this.rawJson = rawJson;
            this.refOpener = refOpener;
            this.registryPick = registryPick;
            this.refKind = refOpener != null ? pickKind(spec) : null;
            // Nested rows have no refOpener; fall back to the sink picker so an
            // `item`/`effect`/… field inside a stack still gets a pick button.
            this.regKind = (refKind == null && registryPick != null)
                ? com.cyberday1.neoorigins.screen.creator.CreatorAssets.registryKind(spec.name())
                : null;
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            boolean hasPick = refKind != null || regKind != null;
            int boxW = hasPick ? Math.max(40, fieldW - 42) : fieldW;
            box = new EditBox(font, 0, 0, boxW, h, Component.literal(spec.name()));
            box.setMaxLength(32767);
            if (spec.defaultValue() != null && !rawJson) box.setValue(String.valueOf(spec.defaultValue()));
            // Raw-JSON fallback fields show a ghost placeholder of the expected
            // shape so an empty box isn't a blank mystery — these have no guided
            // widget, so the shape hint is the only in-line cue.
            if (rawJson) box.setHint(Component.literal(rawJsonHint(spec)));
            parent.register(box);
            if (refKind != null) {
                pick = Button.builder(Component.literal("pick"),
                        b -> refOpener.open(refKind, spec.name()))
                    .bounds(0, 0, 38, h).build();
                parent.register(pick);
            } else if (regKind != null) {
                // Sink picker: set THIS box's value; the overlay's onClose then
                // push()+rebuilds, serialising the new value through the sub-form.
                pick = Button.builder(Component.literal("pick"),
                        b -> registryPick.open(regKind, picked -> box.setValue(picked)))
                    .bounds(0, 0, 38, h).build();
                parent.register(pick);
            }
        }
        /** Ghost placeholder showing the expected JSON shape for a raw-JSON fallback field. */
        private static String rawJsonHint(FormFieldSpec spec) {
            return switch (spec.kind()) {
                case REF    -> "{ \"type\": … }";
                case ARRAY  -> "[ … ]";
                case OBJECT -> "{ … }";
                case MIXED  -> "value or { … }";
                default     -> "JSON";
            };
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
     * underneath. The type picker uses {@link TypePicker} with a row-local
     * sink that just sets {@code currentType}; the supplied
     * {@link Runnable rebuildCb} pushes the row tree to {@code target.rawJson}
     * and triggers a screen rebuild, which re-instantiates this RefRow with
     * the new type and freshly built sub-rows.
     *
     * <p>Nested REF / ARRAY sub-rows receive the same {@code typePicker} and
     * {@code rebuildCb}, so {@link RefRow} and {@link ArrayRefRow} recurse
     * naturally. State lives in the row tree and is serialised via
     * {@link #toJson} — no JSON-path threading needed.
     */
    private static final class RefRow extends Base {
        private static final int HEADER_H = 22;
        private static final int INDENT = 12;
        private static final String EMPTY_LABEL = "(pick %s)";

        private final TypePicker typePicker;
        private final Runnable rebuildCb;
        private final RegistryPick registryPick;
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

        RefRow(FormFieldSpec spec, TypePicker typePicker, Runnable rebuildCb, RegistryPick registryPick) {
            super(spec);
            this.typePicker = typePicker;
            this.rebuildCb = rebuildCb;
            this.registryPick = registryPick;
            this.refKind = refTypeKind(spec);
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            typeButton = Button.builder(typeLabel(),
                    b -> {
                        if (typePicker != null && refKind != null) {
                            typePicker.open(refKind, picked -> {
                                currentType = picked;
                                subRows.clear(); // post-rebuild builds fresh sub-rows for new type
                            });
                        }
                    })
                .bounds(0, 0, Math.max(40, fieldW - 26), h).build();
            parent.register(typeButton);
            clearButton = Button.builder(Component.literal("x"),
                    b -> { currentType = ""; subRows.clear(); if (rebuildCb != null) rebuildCb.run(); })
                .bounds(0, 0, 22, h).build();
            parent.register(clearButton);
            buildSubRows();
        }

        private void buildSubRows() {
            subRows.clear();
            if (currentType.isEmpty() || refKind == null) return;
            List<FormFieldSpec> specs = "action".equals(refKind)
                ? com.cyberday1.neoorigins.power.schemaform.FormModel.forAction(currentType)
                : "block_condition".equals(refKind)
                    ? com.cyberday1.neoorigins.power.schemaform.FormModel.forBlockCondition(currentType)
                    : "item_condition".equals(refKind)
                        ? com.cyberday1.neoorigins.power.schemaform.FormModel.forItemCondition(currentType)
                        : "item_action".equals(refKind)
                            ? com.cyberday1.neoorigins.power.schemaform.FormModel.forItemAction(currentType)
                            : "condition".equals(refKind)
                                ? com.cyberday1.neoorigins.power.schemaform.FormModel.forCondition(currentType)
                                : List.of();
            int subW = Math.max(40, fieldW - INDENT);
            for (FormFieldSpec sub : specs) {
                // Recursive: pass typePicker + rebuildCb so nested REFs / ARRAYs
                // become their own RefRow / ArrayRefRow. Null refOpener +
                // enumOpener — the top-level root-write picker can't address our
                // JSON sub-path — but the sink-based registryPick CAN, so a
                // nested `item`/`effect`/… string still gets a working pick button.
                FieldRow row = FieldWidgetFactory.create(sub, null, null, typePicker, rebuildCb, registryPick);
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

        @Override public FieldRow hoveredRow(int labelX, int y, int fieldRight,
                                             int mouseX, int mouseY) {
            int subY = y + HEADER_H;
            for (FieldRow sub : subRows) {
                FieldRow hit = sub.hoveredRow(
                    labelX + INDENT, subY, fieldRight, mouseX, mouseY);
                if (hit != null) return hit;
                subY += sub.height();
            }
            return super.hoveredRow(labelX, y, fieldRight, mouseX, mouseY);
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

    // ── ARRAY of REFs (and.actions, if_else_list.actions, …) ────────────────

    /**
     * List editor for an array field whose items are action/condition REFs.
     * Renders a header with an {@code add} button (opens the type picker for
     * the new item's type) and N rows, each a {@link RefRow} for one item.
     * Removing an item is just clicking the item RefRow's clear ("x") button —
     * its {@code toJson} returns null on the next push, the array shrinks,
     * and the rebuilt tree drops the row.
     *
     * <p>Adding works by appending a {@code {"type":"<picked>"}} object to a
     * pending-additions list that {@link #toJson} flushes; the post-pick
     * rebuild then re-instantiates this row from the larger array and the
     * new RefRow gets built from scratch with the picked type.
     */
    private static final class ArrayRefRow extends Base {
        private static final int HEADER_H = 22;
        private static final int INDENT = 12;

        private final TypePicker typePicker;
        private final Runnable rebuildCb;
        private final RegistryPick registryPick;
        private final String itemKind; // "action" or "condition"
        private final String itemsRef; // forwarded to inner RefRow as ref()

        private CreatorHost parent;
        private Font font;
        private int fieldW;
        private Button addButton;
        private final List<RefRow> items = new ArrayList<>();
        private final List<JsonObject> pendingAdds = new ArrayList<>();

        ArrayRefRow(FormFieldSpec spec, TypePicker typePicker, Runnable rebuildCb, RegistryPick registryPick) {
            super(spec);
            this.typePicker = typePicker;
            this.rebuildCb = rebuildCb;
            this.registryPick = registryPick;
            this.itemKind = refTypeKind(spec);
            this.itemsRef = spec.itemsRef();
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            addButton = Button.builder(Component.literal("+ add " + (itemKind == null ? "item" : itemKind)),
                    b -> {
                        if (typePicker != null && itemKind != null) {
                            typePicker.open(itemKind, picked -> {
                                JsonObject obj = new JsonObject();
                                obj.addProperty("type", picked);
                                pendingAdds.add(obj);
                            });
                        }
                    })
                .bounds(0, 0, Math.min(fieldW, 140), h).build();
            parent.register(addButton);
        }

        /** Build a synthetic REF spec for one array item, inheriting the items.$ref. */
        private FormFieldSpec itemSpec() {
            return new FormFieldSpec(spec.name() + "[]", FormFieldSpec.Kind.REF,
                false, null, List.of(), null, null, null, itemsRef, null);
        }

        @Override public int height() {
            int h = HEADER_H;
            for (RefRow item : items) h += item.height();
            return h;
        }

        @Override public void reposition(int fieldX, int y) {
            // Add button on the header row, full width.
            addButton.setPosition(fieldX, y);
            int rowY = y + HEADER_H;
            for (RefRow item : items) {
                item.reposition(fieldX + INDENT, rowY);
                rowY += item.height();
            }
        }

        @Override public void setVisible(boolean v) {
            addButton.visible = v; addButton.active = v;
            for (RefRow item : items) item.setVisible(v);
        }

        @Override public void drawLabel(GuiGraphics g, Font font, int labelX, int y) {
            super.drawLabel(g, font, labelX, y);
            int rowY = y + HEADER_H;
            for (RefRow item : items) {
                item.drawLabel(g, font, labelX + INDENT, rowY);
                rowY += item.height();
            }
        }

        @Override public FieldRow hoveredRow(int labelX, int y, int fieldRight,
                                             int mouseX, int mouseY) {
            int rowY = y + HEADER_H;
            for (RefRow item : items) {
                FieldRow hit = item.hoveredRow(
                    labelX + INDENT, rowY, fieldRight, mouseX, mouseY);
                if (hit != null) return hit;
                rowY += item.height();
            }
            return super.hoveredRow(labelX, y, fieldRight, mouseX, mouseY);
        }

        @Override public JsonElement toJson() {
            JsonArray arr = new JsonArray();
            for (RefRow item : items) {
                JsonElement v = item.toJson();
                if (v != null) arr.add(v);
            }
            for (JsonObject pending : pendingAdds) arr.add(pending);
            pendingAdds.clear();
            return arr.size() == 0 ? null : arr;
        }

        @Override public void fromJson(JsonElement el) {
            items.clear();
            if (parent == null) return;
            if (el == null || !el.isJsonArray()) return;
            JsonArray arr = el.getAsJsonArray();
            int subW = Math.max(40, fieldW - INDENT);
            FormFieldSpec is = itemSpec();
            for (JsonElement itemEl : arr) {
                RefRow row = new RefRow(is, typePicker, rebuildCb, registryPick);
                row.build(parent, font, subW, 16);
                row.fromJson(itemEl);
                items.add(row);
            }
        }
    }

    // ── ARRAY of scalar strings (biomes, tags, ids — items.pattern, no $ref) ─

    /**
     * List editor for an array whose items are scalar STRINGs (schema
     * {@code items:{type:"string", pattern:…}}, no {@code items.$ref}) — e.g. a
     * location condition's {@code biomes} list. Renders a {@code + add} button and
     * one {@link EditBox} per entry, each with a remove ({@code x}) button, instead
     * of the raw-JSON fallback. Entries are free-text resource-locations (never a
     * closed enum), so modded/datapack ids work by construction — mirroring the
     * website's {@code ArrayStringRow}.
     *
     * <p>Add/remove mutate the live row list and call {@code rebuildCb}; the panel
     * captures {@link #toJson()} into the draft and rebuilds, re-running
     * {@link #fromJson} to lay rows out at the new height — the same dynamic-height
     * pattern {@link RefRow} / {@link ArrayRefRow} use. A freshly added blank entry
     * round-trips as {@code ""} so its box survives the rebuild; the pack
     * serializer drops blank entries on export (mirroring {@code pruneForWire}).
     */
    private static final class ArrayStringRow extends Base {
        private static final int HEADER_H = 22;
        private static final int ROW_H = 20;
        private static final int INDENT = 12;
        private static final int REMOVE_W = 22;

        private final Runnable rebuildCb;

        private CreatorHost parent;
        private Font font;
        private int fieldW;
        private Button addButton;
        private final List<EditBox> boxes = new ArrayList<>();
        private final List<Button> removeButtons = new ArrayList<>();
        /** Blank entries appended via "+ add" that the next toJson() materialises. */
        private final List<String> pendingAdds = new ArrayList<>();

        ArrayStringRow(FormFieldSpec spec, Runnable rebuildCb) {
            super(spec);
            this.rebuildCb = rebuildCb;
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            addButton = Button.builder(Component.literal("+ add"),
                    b -> { pendingAdds.add(""); if (rebuildCb != null) rebuildCb.run(); })
                .bounds(0, 0, Math.min(fieldW, 80), h).build();
            parent.register(addButton);
        }

        /** Append one entry row (text box + remove button) seeded with {@code value}. */
        private void addRow(String value) {
            int boxW = Math.max(40, fieldW - INDENT - REMOVE_W - 4);
            EditBox box = new EditBox(font, 0, 0, boxW, ROW_H - 2, Component.literal(spec.name()));
            box.setMaxLength(32767);
            box.setValue(value);
            if (spec.itemPattern() != null) box.setHint(Component.literal("resource location"));
            parent.register(box);
            Button rm = Button.builder(Component.literal("x"), b -> {
                    int i = boxes.indexOf(box);
                    if (i >= 0) { boxes.remove(i); removeButtons.remove(i); }
                    if (rebuildCb != null) rebuildCb.run();
                })
                .bounds(0, 0, REMOVE_W, ROW_H - 2).build();
            parent.register(rm);
            boxes.add(box);
            removeButtons.add(rm);
        }

        @Override public int height() {
            return HEADER_H + boxes.size() * ROW_H;
        }

        @Override public void reposition(int fieldX, int y) {
            addButton.setPosition(fieldX, y);
            int rowY = y + HEADER_H;
            for (int i = 0; i < boxes.size(); i++) {
                boxes.get(i).setPosition(fieldX + INDENT, rowY);
                removeButtons.get(i).setPosition(fieldX + fieldW - REMOVE_W, rowY);
                rowY += ROW_H;
            }
        }

        @Override public void setVisible(boolean v) {
            addButton.visible = v; addButton.active = v;
            for (EditBox box : boxes) { box.visible = v; box.active = v; }
            for (Button rm : removeButtons) { rm.visible = v; rm.active = v; }
        }

        @Override public JsonElement toJson() {
            JsonArray arr = new JsonArray();
            for (EditBox box : boxes) arr.add(box.getValue().trim());
            for (String pending : pendingAdds) arr.add(pending);
            pendingAdds.clear();
            return arr.size() == 0 ? null : arr;
        }

        @Override public void fromJson(JsonElement el) {
            boxes.clear();
            removeButtons.clear();
            if (parent == null || el == null || !el.isJsonArray()) return;
            for (JsonElement item : el.getAsJsonArray()) {
                addRow(item.isJsonPrimitive() ? item.getAsString() : item.toString());
            }
        }
    }

    // ── ARRAY of fixed-shape OBJECTs (starting_equipment.stacks, enchantments) ─

    /**
     * List editor for an array whose items are fixed-shape OBJECTs — e.g.
     * {@code starting_equipment.stacks} (each {@code {item, count, enchantments,
     * legacy_tag, components}}) or an {@code enchantments} list of
     * {@code {id, level}}. Renders a {@code + add} button and one
     * {@link ObjectRow} sub-form per entry, each with a remove ({@code x})
     * button, instead of the raw-JSON fallback.
     *
     * <p>Add/remove mutate the live row list and call {@code rebuildCb}; the
     * panel captures {@link #toJson()} into the draft and rebuilds, re-running
     * {@link #fromJson} to lay rows out at the new height — the same dynamic
     * pattern {@link ArrayStringRow} / {@link ArrayRefRow} use. A freshly added
     * entry round-trips as an empty object so its sub-form survives the rebuild;
     * blank stacks are tolerated at grant time (the item field is optional and a
     * missing item logs a skip-WARN rather than failing the whole power).
     */
    private static final class ArrayObjectRow extends Base {
        // Taller than the other list rows: each entry is a full multi-field
        // sub-form, so the header ("Stacks" + "+ add") needs room below it.
        private static final int HEADER_H = 26;
        private static final int INDENT = 12;
        private static final int REMOVE_W = 22;
        /** Blank gap reserved ABOVE every entry; a divider sits near its top so
         *  each entry's sub-form is framed off from the header / the entry above. */
        private static final int ENTRY_GAP = 16;

        private final TypePicker typePicker;
        private final Runnable rebuildCb;
        private final RegistryPick registryPick;

        private CreatorHost parent;
        private Font font;
        private int fieldW;
        private Button addButton;
        private final List<ObjectRow> items = new ArrayList<>();
        private final List<Button> removeButtons = new ArrayList<>();
        /** Field-column x from the last reposition() — lets drawLabel() span the
         *  inter-entry divider across the full row (label column → field edge). */
        private int lastFieldX;
        /** Empty entries appended via "+ add" that the next toJson() materialises. */
        private int pendingAdds = 0;

        ArrayObjectRow(FormFieldSpec spec, TypePicker typePicker, Runnable rebuildCb, RegistryPick registryPick) {
            super(spec);
            this.typePicker = typePicker;
            this.rebuildCb = rebuildCb;
            this.registryPick = registryPick;
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            addButton = Button.builder(Component.literal("+ add"),
                    b -> { pendingAdds++; if (rebuildCb != null) rebuildCb.run(); })
                .bounds(0, 0, Math.min(fieldW, 80), h).build();
            parent.register(addButton);
        }

        /** Synthetic OBJECT spec for one array element, inheriting the element's children. */
        private FormFieldSpec elementSpec() {
            return new FormFieldSpec(spec.name() + "[]", FormFieldSpec.Kind.OBJECT,
                false, null, List.of(), null, null, null, null, null, spec.children());
        }

        /** Append one object entry (inline sub-form + remove button), seeded from {@code el}. */
        private void addRow(JsonElement el) {
            int subW = Math.max(40, fieldW - INDENT - REMOVE_W - 4);
            ObjectRow row = new ObjectRow(elementSpec(), typePicker, rebuildCb, registryPick);
            row.build(parent, font, subW, 16);
            row.fromJson(el);
            Button rm = Button.builder(Component.literal("x"), b -> {
                    int i = items.indexOf(row);
                    if (i >= 0) { items.remove(i); removeButtons.remove(i); }
                    if (rebuildCb != null) rebuildCb.run();
                })
                .bounds(0, 0, REMOVE_W, 16).build();
            parent.register(rm);
            items.add(row);
            removeButtons.add(rm);
        }

        @Override public int height() {
            int h = HEADER_H;
            for (ObjectRow row : items) h += ENTRY_GAP + row.height();
            return h;
        }

        @Override public void reposition(int fieldX, int y) {
            lastFieldX = fieldX;
            addButton.setPosition(fieldX, y);
            int rowY = y + HEADER_H;
            for (int i = 0; i < items.size(); i++) {
                rowY += ENTRY_GAP;
                items.get(i).reposition(fieldX + INDENT, rowY);
                removeButtons.get(i).setPosition(fieldX + fieldW - REMOVE_W, rowY);
                rowY += items.get(i).height();
            }
        }

        @Override public void setVisible(boolean v) {
            addButton.visible = v; addButton.active = v;
            for (ObjectRow row : items) row.setVisible(v);
            for (Button rm : removeButtons) { rm.visible = v; rm.active = v; }
        }

        @Override public void drawLabel(GuiGraphics g, Font font, int labelX, int y) {
            super.drawLabel(g, font, labelX, y);
            int rowY = y + HEADER_H;
            for (int i = 0; i < items.size(); i++) {
                // Divider near the top of the gap above EVERY entry, spanning
                // label column → field edge. Sits clear of the entry below.
                com.cyberday1.neoorigins.screen.creator.CreatorStyle.divider(
                    g, labelX, rowY + 3, (lastFieldX + fieldW) - labelX);
                rowY += ENTRY_GAP;
                items.get(i).drawLabel(g, font, labelX + INDENT, rowY);
                rowY += items.get(i).height();
            }
        }

        @Override public FieldRow hoveredRow(int labelX, int y, int fieldRight,
                                             int mouseX, int mouseY) {
            int rowY = y + HEADER_H;
            for (int i = 0; i < items.size(); i++) {
                rowY += ENTRY_GAP;
                FieldRow hit = items.get(i).hoveredRow(
                    labelX + INDENT, rowY, fieldRight, mouseX, mouseY);
                if (hit != null) return hit;
                rowY += items.get(i).height();
            }
            return super.hoveredRow(labelX, y, fieldRight, mouseX, mouseY);
        }

        @Override public JsonElement toJson() {
            JsonArray arr = new JsonArray();
            for (ObjectRow row : items) {
                JsonElement v = row.toJson();
                // Keep an empty {} for an untouched entry so its sub-form survives
                // the rebuild; the grant path tolerates a stack with no item.
                arr.add(v == null ? new JsonObject() : v);
            }
            for (int i = 0; i < pendingAdds; i++) arr.add(new JsonObject());
            pendingAdds = 0;
            return arr.size() == 0 ? null : arr;
        }

        @Override public void fromJson(JsonElement el) {
            items.clear();
            removeButtons.clear();
            if (parent == null || el == null || !el.isJsonArray()) return;
            for (JsonElement item : el.getAsJsonArray()) addRow(item);
        }
    }

    // ── OBJECT with a fixed child set (item stack, effect instance, hud_render) ─

    /**
     * Inline sub-form for an {@link FormFieldSpec.Kind#OBJECT} that carries a
     * FIXED set of {@link FormFieldSpec#children() children} (e.g. an item stack
     * {@code {item, count}}, an effect instance, or the {@code resource} power's
     * {@code hud_render} block). Unlike {@link RefRow} there is NO type to pick —
     * the children are always the same — so this renders just an indented column
     * of child rows under the field label, with no header widget of its own.
     *
     * <p>{@code typePicker} / {@code rebuildCb} / {@code registryPick} are
     * forwarded so a nested REF / ARRAY child still becomes its own
     * {@link RefRow} / {@link ArrayRefRow}, and a nested registry string (an item
     * stack's {@code item}, …) gets a sink-based "pick" button. Large-enum
     * dropdowns remain disabled at depth (they route through the top-level write
     * path that doesn't know this object's JSON sub-path). State lives in the
     * child rows and serialises via {@link #toJson()}.
     */
    private static final class ObjectRow extends Base {
        private static final int HEADER_H = 20;
        private static final int INDENT = 12;

        private final TypePicker typePicker;
        private final Runnable rebuildCb;
        private final RegistryPick registryPick;

        private CreatorHost parent;
        private Font font;
        private int fieldW;
        private final List<FieldRow> subRows = new ArrayList<>();
        /** Per-sub-row y offset (relative to this row's top), set on reposition. */
        private final List<Integer> subYs = new ArrayList<>();

        ObjectRow(FormFieldSpec spec, TypePicker typePicker, Runnable rebuildCb, RegistryPick registryPick) {
            super(spec);
            this.typePicker = typePicker;
            this.rebuildCb = rebuildCb;
            this.registryPick = registryPick;
        }

        @Override public void build(CreatorHost parent, Font font, int fieldW, int h) {
            this.parent = parent;
            this.font = font;
            this.fieldW = fieldW;
            subRows.clear();
            int subW = Math.max(40, fieldW - INDENT);
            for (FormFieldSpec child : spec.children()) {
                FieldRow row = FieldWidgetFactory.create(child, null, null, typePicker, rebuildCb, registryPick);
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
            subYs.clear();
            int subY = y + HEADER_H;
            for (FieldRow sub : subRows) {
                subYs.add(subY);
                sub.reposition(fieldX + INDENT, subY);
                subY += sub.height();
            }
        }

        @Override public void setVisible(boolean v) {
            for (FieldRow sub : subRows) sub.setVisible(v);
        }

        @Override public void drawLabel(GuiGraphics g, Font font, int labelX, int y) {
            super.drawLabel(g, font, labelX, y);
            for (int i = 0; i < subRows.size() && i < subYs.size(); i++) {
                subRows.get(i).drawLabel(g, font, labelX + INDENT, subYs.get(i));
            }
        }

        @Override public FieldRow hoveredRow(int labelX, int y, int fieldRight,
                                             int mouseX, int mouseY) {
            int subY = y + HEADER_H;
            for (FieldRow sub : subRows) {
                FieldRow hit = sub.hoveredRow(
                    labelX + INDENT, subY, fieldRight, mouseX, mouseY);
                if (hit != null) return hit;
                subY += sub.height();
            }
            return super.hoveredRow(labelX, y, fieldRight, mouseX, mouseY);
        }

        @Override public JsonElement toJson() {
            JsonObject body = new JsonObject();
            for (FieldRow sub : subRows) {
                JsonElement v = sub.toJson();
                if (v != null) body.add(sub.fieldName(), v);
            }
            // Drop an untouched optional object rather than leaking an empty {}.
            return body.size() == 0 ? null : body;
        }

        @Override public void fromJson(JsonElement el) {
            JsonObject body = (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
            for (FieldRow sub : subRows) {
                sub.fromJson(body == null ? null : body.get(sub.fieldName()));
            }
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
