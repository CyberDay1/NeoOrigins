package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.api.origin.Impact;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Identity tab — id path / display name / description / icon / impact / order,
 * bound to the shared {@link OriginDraft}. Icon is a parsed id field here; the
 * searchable item picker arrives in 4c.
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

    private OriginCreatorScreen parent;
    private int rowY;

    @Override public Component title() { return TITLE; }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        rowY = y + 14;
        int fieldW = Math.min(w - FIELD_DX - 8, 220);
        Font font = parent.font();
        int fx = x + FIELD_DX;

        parent.register(idPath.build(font, fx, rowY, fieldW, BOX_H));
        parent.register(name.build(font, fx, rowY + ROW_H, fieldW, BOX_H));
        parent.register(description.build(font, fx, rowY + ROW_H * 2, fieldW, BOX_H));
        parent.register(icon.build(font, fx, rowY + ROW_H * 3, fieldW, BOX_H));
        parent.register(impact.build(fx, rowY + ROW_H * 4, 90, 20));
        parent.register(order.build(font, fx, rowY + ROW_H * 5, 60, BOX_H));
    }

    @Override
    public void pullFromDraft() {
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
        Font font = parent.font();
        int lx = x + LABEL_DX;
        idPath.drawLabel(g, font, lx, rowY + 4);
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        g.text(font, "impact", lx, rowY + ROW_H * 4 + 6, 0xFFBBBBCC, false);
        order.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);
    }
}
