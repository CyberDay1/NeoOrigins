package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.ItemPickerOverlay;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Identity tab — id path / display name / description / icon / impact / order,
 * bound to the shared {@link OriginDraft}. The icon is editable as a parsed id
 * field or chosen from the registry-backed {@link ItemPickerOverlay}.
 */
public final class IdentityTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.identity");

    private static final int LABEL_DX = 8, FIELD_DX = 100, ROW_H = 24, BOX_H = 16;

    private final LabeledField idPath = new LabeledField("id path");
    private final LabeledField name = new LabeledField("name");
    private final LabeledField description = new LabeledField("description");
    private final LabeledField icon = new LabeledField("icon");
    private final LabeledField order = new LabeledField("order", LabeledField.intFilter());
    private final CycleSelector<Integer> impact =
        new CycleSelector<>(List.of(0, 1, 2, 3), i -> Impact.values()[i].name());
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    private OriginCreatorScreen parent;
    private int rowY;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Who is this origin: id, display name, description, icon and impact.");
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;

        if (itemPicker.isOpen()) {
            // Overlay owns the screen's input while open — build only it.
            int pw = Math.min(w - 20, 320), ph = h - 16;
            itemPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }

        rowY = y + 14;
        int fieldW = Math.min(w - FIELD_DX - 8, 220);
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
    }

    private void openPicker() {
        pushToDraft(); // keep the other identity edits
        itemPicker.open(false,
            (id, comp) -> {
                try { parent.draft().icon = Identifier.parse(id); }
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
        try { d.icon = Identifier.parse(icon.value().trim()); }
        catch (RuntimeException ignored) { /* keep prior icon if unparseable */ }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (itemPicker.isOpen()) { itemPicker.render(g); return; }
        Font font = parent.font();
        int lx = x + LABEL_DX;
        CreatorStyle.sectionHeader(g, font, "Origin basics", lx, y, w - LABEL_DX * 2);
        idPath.drawLabel(g, font, lx, rowY + 4);
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        g.text(font, "impact", lx, rowY + ROW_H * 4 + 6, CreatorStyle.LABEL, false);
        order.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return itemPicker.isOpen() && itemPicker.onScroll(mx, my, sy);
    }
}
