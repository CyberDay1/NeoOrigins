package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.widget.ItemPickerOverlay;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Identity tab — id / name / description / icon and the entity target
 * (single type id or a tag). Multi-type targets are supported by the model /
 * on-disk codec but not this v1 UI (use the JSON tab for that rare case).
 */
public final class MobIdentityTab implements MobCreatorTab {

    private static final int LABEL_DX = 8, FIELD_DX = 110, ROW_H = 24, BOX_H = 16;

    private final LabeledField idPath = new LabeledField("id path");
    private final LabeledField name = new LabeledField("name");
    private final LabeledField description = new LabeledField("description");
    private final LabeledField icon = new LabeledField("icon");
    private final LabeledField targetType = new LabeledField("target entity");
    private final LabeledField targetTag = new LabeledField("target tag");
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    private MobOriginCreatorScreen parent;
    private int rowY;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.identity");
    }
    @Override public Component help() {
        return Component.literal("Name, icon, and which mob(s) this origin rolls onto.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        if (itemPicker.isOpen()) {
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
        parent.register(targetType.build(font, fx, rowY + ROW_H * 4, fieldW, BOX_H));
        parent.register(targetTag.build(font, fx, rowY + ROW_H * 5, fieldW, BOX_H));
    }

    private void openPicker() {
        pushToDraft();
        itemPicker.open(false,
            (id, comp) -> {
                Identifier rl = Identifier.tryParse(id);
                if (rl != null) parent.draft().icon = rl;
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    @Override
    public void pullFromDraft() {
        if (itemPicker.isOpen()) return;
        MobOriginDraft d = parent.draft();
        idPath.setValue(d.idPath);
        name.setValue(d.name);
        description.setValue(d.description);
        icon.setValue(d.icon.toString());
        targetType.setValue(d.targetEntityType);
        targetTag.setValue(d.targetEntityTag);
    }

    @Override
    public void pushToDraft() {
        if (itemPicker.isOpen()) return;
        MobOriginDraft d = parent.draft();
        d.idPath = idPath.value().trim();
        d.name = name.value();
        d.description = description.value();
        Identifier ic = Identifier.tryParse(icon.value().trim());
        if (ic != null) d.icon = ic;
        d.targetEntityType = targetType.value().trim();
        d.targetEntityTag = targetTag.value().trim();
    }

    @Override
    public void renderBackdrop(GuiGraphicsExtractor g) {
        if (itemPicker.isOpen()) itemPicker.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (itemPicker.isOpen()) { itemPicker.render(g); return; }
        Font font = parent.font();
        int lx = x + LABEL_DX;
        CreatorStyle.sectionHeader(g, font, "Mob origin basics", lx, y, w - LABEL_DX * 2);
        idPath.drawLabel(g, font, lx, rowY + 4);
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        targetType.drawLabel(g, font, lx, rowY + ROW_H * 4 + 4);
        targetTag.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);
        g.text(font, "Set ONE of: target entity (e.g. minecraft:zombie) "
                + "or target tag (e.g. minecraft:undead).",
            lx, rowY + ROW_H * 6 + 6, CreatorStyle.TEXT_DIM, false);

        String[] tips = {
            "Datapack id (lowercase a-z/0-9/_). Becomes neoorigins_custom:<id>.",
            "Display name (DM browser only; never shown in-world).",
            "Flavor text for the creator browser.",
            "Item id used as this mob origin's icon. Click Pick to browse.",
            "Exact entity type to roll onto, e.g. minecraft:zombie.",
            "OR an entity-type tag, e.g. minecraft:undead (leave entity blank)."
        };
        for (int i = 0; i < tips.length; i++) {
            int top = rowY + ROW_H * i;
            if (mouseY >= top && mouseY < top + ROW_H && mouseX >= lx && mouseX <= x + w) {
                parent.queueTooltip(java.util.List.of(tips[i]), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return itemPicker.isOpen() && itemPicker.onScroll(mx, my, sy);
    }
}
