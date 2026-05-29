package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.ItemPickerOverlay;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identity tab — id / name / description / icon / impact / order and the
 * target layer, bound to the shared {@link OriginDraft}. Layer used to be its
 * own tab but it is a single A/B-ish choice, so it lives here as one more
 * field. Icon is editable as a parsed id or chosen from the registry-backed
 * {@link ItemPickerOverlay}.
 */
public final class IdentityTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.identity");
    private static final ResourceLocation CLASS_LAYER =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "class");

    private static final int LABEL_DX = 8, FIELD_DX = 100, ROW_H = 24, BOX_H = 16;

    private final LabeledField idPath = new LabeledField("id");
    private final LabeledField name = new LabeledField("name");
    private final LabeledField description = new LabeledField("description");
    private final LabeledField icon = new LabeledField("icon");
    private final LabeledField order = new LabeledField("order", LabeledField.intFilter());
    private final CycleSelector<Integer> impact =
        new CycleSelector<>(List.of(0, 1, 2, 3), i -> Impact.values()[i].name());
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    private final Map<ResourceLocation, String> layerNames = new LinkedHashMap<>();
    private CycleSelector<ResourceLocation> layer;

    private OriginCreatorScreen parent;
    private int rowY, layerHdrY, layerRowY;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Name, icon, impact, and which picker (or class) this origin appears in.");
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;

        if (itemPicker.isOpen()) {
            // Overlay owns the screen's input while open — build only it.
            int pw = Math.min(w - 20, 340), ph = h - 16;
            itemPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }

        rowY = y + 14;
        int fieldW = Math.min(w - FIELD_DX - 8, 240);
        Font font = parent.font();
        int fx = x + FIELD_DX;

        parent.register(idPath.build(font, fx, rowY, fieldW, BOX_H));
        parent.register(name.build(font, fx, rowY + ROW_H, fieldW, BOX_H));
        parent.register(description.build(font, fx, rowY + ROW_H * 2, fieldW, BOX_H));
        parent.register(icon.build(font, fx, rowY + ROW_H * 3, fieldW - 44, BOX_H));
        parent.register(Button.builder(Component.literal("pick"), b -> openPicker())
            .bounds(fx + fieldW - 40, rowY + ROW_H * 3 - 2, 40, BOX_H + 4).build());
        parent.register(impact.build(fx, rowY + ROW_H * 4, 90, 20));
        parent.register(order.build(font, fx, rowY + ROW_H * 5, 60, BOX_H));

        // ── Layer (folded in from the old Layer tab) ──────────────────────
        layerHdrY = rowY + ROW_H * 6 + 4;
        layerRowY = layerHdrY + 16;
        layerNames.clear();
        List<OriginLayer> layers = LayerDataManager.INSTANCE.getSortedLayers();
        for (OriginLayer l : layers) layerNames.put(l.id(), l.name().getString());
        List<ResourceLocation> ids = layers.isEmpty()
            ? List.of(parent.draft().layerId)
            : List.copyOf(layerNames.keySet());
        layer = new CycleSelector<>(ids,
            id -> layerNames.getOrDefault(id, id.toString()) + "  (" + id + ")");
        parent.register(layer.build(fx, layerRowY, Math.min(w - FIELD_DX - 8, 300), 20));
    }

    private void openPicker() {
        pushToDraft(); // keep the other identity edits
        itemPicker.open(false,
            (id, comp) -> {
                try { parent.draft().icon = ResourceLocation.parse(id); }
                catch (RuntimeException ignored) { /* keep prior icon */ }
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    @Override
    public void pullFromDraft() {
        if (itemPicker.isOpen()) return;
        OriginDraft d = parent.draft();
        // Show the full ResourceLocation (ns:path) so a templated vanilla
        // origin reads as e.g. "neoorigins:abyssal", making the override
        // intent visible. The default custom namespace renders as bare path
        // ("my_origin") so first-time users aren't confronted with the prefix.
        idPath.setValue(formatIdField(d));
        name.setValue(d.name);
        description.setValue(d.description);
        icon.setValue(d.icon.toString());
        order.setValue(Integer.toString(d.order));
        impact.setValue(d.impact);
        if (layer != null) layer.setValue(d.layerId);
    }

    @Override
    public void pushToDraft() {
        if (itemPicker.isOpen()) return;
        OriginDraft d = parent.draft();
        parseIdField(idPath.value().trim(), d);
        d.name = name.value();
        d.description = description.value();
        d.impact = impact.value();
        try { d.order = Integer.parseInt(order.value().trim()); }
        catch (NumberFormatException ignored) { /* keep prior value */ }
        try { d.icon = ResourceLocation.parse(icon.value().trim()); }
        catch (RuntimeException ignored) { /* keep prior icon if unparseable */ }
        if (layer != null) d.layerId = layer.value();
    }

    @Override
    public void renderBackdrop(GuiGraphics g) {
        if (itemPicker.isOpen()) itemPicker.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (itemPicker.isOpen()) { itemPicker.render(g); return; }
        Font font = parent.font();
        int lx = x + LABEL_DX;
        CreatorStyle.sectionHeader(g, font, "Origin basics", lx, y, w - LABEL_DX * 2);
        idPath.drawLabel(g, font, lx, rowY + 4);

        // Override indicator near the id field. Renders here so it travels
        // with a templated draft; the matching "?" tooltip is queued at the
        // bottom of this method (after the row-hover loop) so it wins.
        OriginDraft d = parent.draft();
        boolean overrides = "neoorigins".equals(d.namespace)
            && OriginDataManager.INSTANCE.getOrigin(
                ResourceLocation.fromNamespaceAndPath("neoorigins", d.idPath)) != null;
        int markX = x + w - LABEL_DX - 10;
        int markY = rowY + 4;
        g.drawString(font, overrides ? "[!] ?" : "?", markX, markY,
            overrides ? CreatorStyle.ACCENT : CreatorStyle.TEXT_DIM, false);
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        g.drawString(font, "Impact", lx, rowY + ROW_H * 4 + 6, CreatorStyle.LABEL, false);
        order.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);

        CreatorStyle.sectionHeader(g, font, "Layer", lx, layerHdrY, w - LABEL_DX * 2);
        g.drawString(font, "Layer", lx, layerRowY + 6, CreatorStyle.LABEL, false);
        boolean isClass = layer != null && CLASS_LAYER.equals(layer.value());
        g.drawString(font,
            isClass ? "This origin will be a CLASS (neoorigins:class layer)."
                    : "Appears as a normal origin in the chosen picker.",
            lx, layerRowY + 26, isClass ? CreatorStyle.ACCENT : CreatorStyle.TEXT_DIM, false);

        // Hover help for every Identity field.
        String tip = null;
        for (int i = 0; i < TIPS.length; i++) {
            int top = rowY + ROW_H * i;
            if (mouseY >= top && mouseY < top + ROW_H && mouseX >= lx && mouseX <= x + w) {
                tip = TIPS[i];
                break;
            }
        }
        if (tip == null && mouseY >= layerRowY && mouseY < layerRowY + 20
                && mouseX >= lx && mouseX <= x + w) {
            tip = "Which picker this appears in. neoorigins:class makes it a class.";
        }
        if (tip != null) {
            parent.queueTooltip(java.util.List.of(tip), mouseX, mouseY);
        }

        // "?" marker hover — queued LAST so it wins over the id-row tip
        // (the marker sits inside the id row and would otherwise be hidden
        // by TIPS[0]).
        if (mouseX >= markX - 2 && mouseX <= markX + 18
                && mouseY >= markY - 2 && mouseY <= markY + 10) {
            String msg = overrides
                ? "OVERRIDES vanilla neoorigins:" + d.idPath
                    + ". Save will write data/neoorigins/origins/origins/"
                    + d.idPath + ".json — the shipped origin is replaced wholesale."
                    + " Change the namespace (e.g. drop \"neoorigins:\") to keep both."
                : "Bare ids land under neoorigins_custom:. Prefix with"
                    + " \"namespace:\" to write elsewhere; use \"neoorigins:<id>\""
                    + " to override a vanilla origin in place.";
            parent.queueTooltip(java.util.List.of(msg), mouseX, mouseY);
        }
    }

    private static final String[] TIPS = {
        "Datapack id. Bare path (e.g. \"my_origin\") lives under neoorigins_custom:."
            + " Use \"namespace:path\" to write under a different namespace —"
            + " set it to neoorigins:<vanilla_id> to OVERRIDE the shipped origin.",
        "Display name shown in the origin picker.",
        "Flavor text shown under the name in the picker.",
        "Item used as this origin's icon. Click Pick to browse all items.",
        "Origins impact rating (NONE..HIGH) — the dots in the picker.",
        "Sort order in the picker; lower numbers appear first."
    };

    /** Render the draft's namespace:idPath back into the editable id field;
     *  hides the default custom namespace prefix to keep the new-draft UX
     *  clean while still surfacing a templated vanilla id ("neoorigins:abyssal"). */
    private static String formatIdField(OriginDraft d) {
        String ns = d.namespace == null || d.namespace.isBlank()
            ? OriginDraft.CUSTOM_NAMESPACE : d.namespace;
        return OriginDraft.CUSTOM_NAMESPACE.equals(ns) ? d.idPath : ns + ":" + d.idPath;
    }

    /** Inverse of {@link #formatIdField}: split a typed value on the FIRST
     *  colon to namespace + idPath. Bare input (no colon) resets namespace to
     *  the default so the user can always type a normal id without thinking
     *  about prefixes. Invalid characters are caught by CreatorValidator on
     *  Save — we just store the trimmed string here. */
    private static void parseIdField(String raw, OriginDraft d) {
        int colon = raw.indexOf(':');
        if (colon < 0) {
            d.namespace = OriginDraft.CUSTOM_NAMESPACE;
            d.idPath = raw;
        } else {
            d.namespace = raw.substring(0, colon).trim();
            d.idPath = raw.substring(colon + 1).trim();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return itemPicker.isOpen() && itemPicker.onScroll(mx, my, sy);
    }
}
