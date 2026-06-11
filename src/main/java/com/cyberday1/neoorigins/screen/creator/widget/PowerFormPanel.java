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
import net.minecraft.client.gui.components.AbstractWidget;
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
    /** Field-row widgets registered input-only, drawn by us inside the scroll
     *  scissor (see {@link #formHost}) so they can't bleed past the viewport. */
    private final List<AbstractWidget> clipped = new ArrayList<>();
    private final CreatorHost formHost = new FormHost();
    private EditBox rawBox;
    private final SearchPickerOverlay refPicker = new SearchPickerOverlay();

    /**
     * Host wrapper handed to the field rows during {@link #buildForm}. It routes
     * each widget to the real host as INPUT-ONLY (so {@code Screen.render} won't
     * auto-draw it unclipped) and collects it into {@link #clipped} for this
     * panel to render inside {@link ScrollPanel#beginClip}/{@link ScrollPanel#endClip}.
     * Everything else delegates straight through to the owning screen.
     */
    private final class FormHost implements CreatorHost {
        @Override
        public <T extends AbstractWidget> T register(T widget) {
            clipped.add(widget);
            return parent.registerInputOnly(widget);
        }
        @Override
        public <T extends AbstractWidget> T registerInputOnly(T widget) {
            return parent.registerInputOnly(widget);
        }
        @Override public Font font() { return parent.font(); }
        @Override public void requestRebuild() { parent.requestRebuild(); }
        @Override public int hostWidth() { return parent.hostWidth(); }
        @Override public int hostHeight() { return parent.hostHeight(); }
        @Override public void queueTooltip(List<String> lines, int mx, int my) {
            parent.queueTooltip(lines, mx, my);
        }
    }

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
        rawBox = null;

        if (refPicker.isOpen()) {                 // overlay owns input
            // Keep `rows` INTACT while the overlay is up. The sink-based registry
            // and type pickers (openRegistryPick / openTypePicker) mutate a live
            // row's widget and then push() the whole row tree on close. Clearing
            // rows here would orphan that widget and make the close-time push()
            // serialise an EMPTY form — wiping the power's config. That is exactly
            // why a freshly-added starting_equipment stack vanished the instant you
            // picked its item (tester report, 2.2.0). Only `clipped` is cleared so
            // stale form widgets don't linger; the tab skips form.render() while
            // the overlay owns the screen, so the preserved rows aren't drawn.
            clipped.clear();
            int pw = Math.min(w - 20, 360), ph = h - 16;
            refPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        rows.clear();
        clipped.clear();
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

        // starting_equipment carries BOTH a legacy singular shape (item/count/…
        // at the root) and the plural `stacks` array. Showing all five singular
        // fields above the array buries the "+ add" and confuses authors about
        // where to add a second item (tester report, 2.2.0). In the in-game
        // editor we present ONLY grant_id + stacks: any existing singular config
        // is folded into a one-entry `stacks` so nothing is lost, and the
        // singular field rows are filtered out below.
        if (type != null && "neoorigins:starting_equipment".equals(type.toString())) {
            specs = collapseStartingEquipment(specs, body);
            target.rawJson = body.toString();
        }
        Runnable rebuildCb = () -> { push(); parent.requestRebuild(); };
        for (FormFieldSpec spec : specs) {
            FieldRow row = FieldWidgetFactory.create(spec, this::openRefPicker, this::openEnumPicker,
                this::openTypePicker, rebuildCb, this::openRegistryPick);
            // Build against formHost so the row's EditBoxes/Buttons register
            // input-only and land in `clipped` — rendered inside the scissor by
            // render(), never auto-drawn unclipped by Screen.super.render().
            row.build(formHost, font, fieldW, 16);
            row.fromJson(body.get(spec.name()));
            rows.add(row);
        }
        int total = 4;
        for (FieldRow row : rows) total += row.height();
        scroll.setContentHeight(total);
    }

    /**
     * For starting_equipment: fold any legacy singular root config into a
     * one-entry {@code stacks} array (so authored singular packs survive into
     * the array editor) and return a spec list with the singular fields removed,
     * leaving just {@code grant_id} and {@code stacks}. Mutates {@code body} in
     * place. The runtime still accepts both shapes — this is purely an editor
     * presentation choice so there's exactly one obvious place to add items.
     */
    private static List<FormFieldSpec> collapseStartingEquipment(List<FormFieldSpec> specs, JsonObject body) {
        boolean hasStacks = body.has("stacks") && body.get("stacks").isJsonArray()
            && !body.getAsJsonArray("stacks").isEmpty();
        String singularItem = body.has("item") && body.get("item").isJsonPrimitive()
            ? body.get("item").getAsString() : "";
        if (!hasStacks && !singularItem.isBlank()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", singularItem);
            if (body.has("count")) entry.add("count", body.get("count"));
            if (body.has("enchantments")) entry.add("enchantments", body.get("enchantments"));
            if (body.has("legacy_tag")) entry.add("legacy_tag", body.get("legacy_tag"));
            if (body.has("components")) entry.add("components", body.get("components"));
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            arr.add(entry);
            body.add("stacks", arr);
        }
        // Drop the singular keys from the body so the JSON tab is clean and the
        // hidden fields can't shadow the stacks the editor now owns.
        body.remove("item");
        body.remove("count");
        body.remove("enchantments");
        body.remove("legacy_tag");
        body.remove("components");

        List<FormFieldSpec> kept = new ArrayList<>();
        for (FormFieldSpec spec : specs) {
            switch (spec.name()) {
                case "item", "count", "enchantments", "legacy_tag", "components" -> { /* hidden */ }
                default -> kept.add(spec);
            }
        }
        return kept;
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
            : "block_condition".equals(kind)
                ? com.cyberday1.neoorigins.power.schemaform.FormModel.blockConditionTypes()
                : "item_condition".equals(kind)
                    ? com.cyberday1.neoorigins.power.schemaform.FormModel.itemConditionTypes()
                    : "item_action".equals(kind)
                        ? com.cyberday1.neoorigins.power.schemaform.FormModel.itemActionTypes()
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
     * Sink-based registry picker for a field nested inside a sub-form (an item
     * stack's {@code item}, etc.). The row's {@code sink} sets its own widget
     * value; while the overlay is open {@link #init} early-returns on
     * {@code refPicker.isOpen()} WITHOUT clearing {@link #rows}, so the live row
     * tree survives to receive the pick. On close we {@link #push()} that intact
     * tree — serialising the new value through the nesting — and rebuild. This is
     * the depth-safe counterpart to {@link #openRefPicker}, which writes a flat
     * root key and can't address a sub-path.
     */
    private void openRegistryPick(String kind, java.util.function.Consumer<String> sink) {
        push();
        java.util.List<String> src = com.cyberday1.neoorigins.screen.creator.CreatorAssets
            .registryList(kind); // already sorted
        refPicker.open("pick " + kind, () -> src,
            sink::accept,
            () -> { push(); parent.requestRebuild(); });
        parent.requestRebuild();
    }

    /**
     * Open the picker for {@code field}. condition/action insert a
     * {@code {"type":"<id>"}} skeleton; a registry kind (particle, item, …)
     * inserts the plain id string. Then rebuild so the form reflects it.
     */
    private void openRefPicker(String kind, String field) {
        push(); // keep edits to the other fields
        boolean dsl = "condition".equals(kind) || "action".equals(kind)
            || "block_condition".equals(kind) || "item_condition".equals(kind)
            || "item_action".equals(kind);
        java.util.List<String> src;
        if (dsl) {
            src = new ArrayList<>("action".equals(kind)
                ? com.cyberday1.neoorigins.compat.action.ActionParser.KNOWN_TYPES
                : "block_condition".equals(kind)
                    ? com.cyberday1.neoorigins.power.schemaform.FormModel.blockConditionTypes()
                    : "item_condition".equals(kind)
                        ? com.cyberday1.neoorigins.power.schemaform.FormModel.itemConditionTypes()
                        : "item_action".equals(kind)
                            ? com.cyberday1.neoorigins.power.schemaform.FormModel.itemActionTypes()
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
     *  Variable-height aware — RefRow expands by its sub-form's row count, and
     *  rows grow when an inline validation error line appears, so the content
     *  height is recomputed here too (this runs every frame from render()). */
    public void layout() {
        int fieldX = x + FIELD_DX;
        int rowTop = scroll.contentTop();
        int total = 4;
        for (FieldRow row : rows) {
            int rh = row.height();
            row.reposition(fieldX, rowTop);
            row.setVisible(!rawMode && scroll.rowVisible(rowTop, rh));
            rowTop += rh;
            total += rh;
        }
        if (!rawMode && target != null) scroll.setContentHeight(total);
    }

    /** Count of blocking field problems currently visible in this panel
     *  (raw mode: 1 if the JSON doesn't parse to an object; form mode: sum of
     *  the rows' own + nested errors). Used by the tabs' "N field problems"
     *  counter — advisory only, the save gate re-validates the draft. */
    public int errorCount() {
        if (target == null) return 0;
        if (rawMode) return rawJsonError() == null ? 0 : 1;
        int n = 0;
        for (FieldRow row : rows) n += row.errorCount();
        return n;
    }

    /** Raw-mode inline error: null when the box holds a JSON object. */
    private String rawJsonError() {
        String s = rawBox != null ? rawBox.getValue() : target.rawJson;
        if (s == null || s.isBlank()) return null; // treated as {}
        try {
            return JsonParser.parseString(s).isJsonObject()
                ? null : "must be a JSON object { … }";
        } catch (RuntimeException e) {
            return "not valid JSON";
        }
    }

    public void pull() {
        if (target == null) return;
        if (rawMode) {
            if (rawBox != null) rawBox.setValue(target.rawJson == null ? "{}" : target.rawJson);
            return;
        }
        // Form mode is a no-op: buildForm() already seeded every row from
        // target.rawJson during init(), and pull() always runs immediately after
        // init() (screen.rebuild → tab.init → form.init/buildForm, then
        // tab.pullFromDraft → form.pull) with the SAME JSON. Re-seeding here would
        // be redundant — and for array/object rows fromJson() RE-INSTANTIATES the
        // sub-rows, registering a fresh widget set while the previous one stays
        // registered with the screen (input-only) and in `clipped` as orphans.
        // Those orphans only show once layout() repositions the live tree on
        // scroll, producing the duplicated/overlapping fields. So: don't re-seed.
    }

    public void push() {
        if (target == null) return;
        if (rawMode) {
            if (rawBox != null) target.rawJson = rawBox.getValue();
            return;
        }
        // Patch-don't-replace: seed from the current JSON so keys the form does
        // not model (common name/description/hidden/required_mods, hand-authored
        // extras) survive a form edit. Each modeled row then owns its key —
        // writing its value, or removing the key when the row is unset.
        JsonObject body = parseObject(target.rawJson);
        for (FieldRow row : rows) {
            JsonElement v = row.toJson();
            if (v != null) body.add(row.fieldName(), v);
            else body.remove(row.fieldName());
        }
        target.rawJson = body.toString();
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (target == null) return;
        Font font = parent.font();

        if (rawMode) {
            String err = rawJsonError();
            if (err != null) {
                g.text(font, "✕ " + err, x + LABEL_DX, y - 10,
                    com.cyberday1.neoorigins.screen.creator.CreatorStyle.ERR, false);
            } else {
                g.text(font,
                    "Editing this power's config as JSON — the \"type\" is added for you on save.",
                    x + LABEL_DX, y - 10,
                    com.cyberday1.neoorigins.screen.creator.CreatorStyle.TEXT_DIM, false);
            }
            return;
        }
        if (rows.isEmpty()) {
            com.cyberday1.neoorigins.screen.creator.CreatorStyle.emptyState(g, font,
                "This power has no editable fields — switch to JSON to configure it.",
                x + w / 2, y + 12);
            return;
        }

        // Rows change height live as validation errors appear/clear while
        // typing — re-layout every frame so widget positions and the scroll
        // content height stay in sync with the current error state.
        layout();

        scroll.beginClip(g);
        // Field widgets (EditBox/Button) are registered input-only, so we draw
        // them here, inside the scissor — that's what keeps a partially-scrolled
        // or oversized row clipped to the viewport instead of bleeding over the
        // header / save bar / neighbouring rows. Off-view rows have visible=false
        // (set in layout()) so their widgets self-skip.
        for (AbstractWidget widget : clipped) {
            widget.extractRenderState(g, mouseX, mouseY, 0f);
        }
        int rowTop = scroll.contentTop();
        FieldRow hovered = null;
        for (FieldRow row : rows) {
            int rh = row.height();
            if (scroll.rowVisible(rowTop, rh)) {
                row.drawLabel(g, font, x + LABEL_DX, rowTop + 4);
                // Recurse the row tree so a nested sub-form field (an item
                // stack's Item/Count, a REF's body field, …) reports its own
                // tooltip; container rows return the deepest hovered child and
                // fall back to their own header. Mirror drawLabel's origin.
                if (hovered == null) {
                    FieldRow hit = row.hoveredRow(
                        x + LABEL_DX, rowTop + 4, x + w, mouseX, mouseY);
                    if (hit != null) hovered = hit;
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
