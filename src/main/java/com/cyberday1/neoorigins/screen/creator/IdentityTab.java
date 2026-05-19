package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.data.LayerDataManager;
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

    private final LabeledField idPath = new LabeledField("id path");
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
        idPath.setValue(d.idPath);
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
        d.idPath = idPath.value().trim();
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
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        g.drawString(font, "impact", lx, rowY + ROW_H * 4 + 6, CreatorStyle.LABEL, false);
        order.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);

        CreatorStyle.sectionHeader(g, font, "Layer", lx, layerHdrY, w - LABEL_DX * 2);
        g.drawString(font, "layer", lx, layerRowY + 6, CreatorStyle.LABEL, false);
        boolean isClass = layer != null && CLASS_LAYER.equals(layer.value());
        g.drawString(font,
            isClass ? "This origin will be a CLASS (neoorigins:class layer)."
                    : "Appears as a normal origin in the chosen picker.",
            lx, layerRowY + 26, isClass ? CreatorStyle.ACCENT : CreatorStyle.TEXT_DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return itemPicker.isOpen() && itemPicker.onScroll(mx, my, sy);
    }
}
