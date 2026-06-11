package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.widget.ItemPickerOverlay;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.cyberday1.neoorigins.screen.creator.widget.SearchPickerOverlay;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Identity tab — id / name / description / icon and the entity target
 * (single type id or a tag). Multi-type targets are supported by the model /
 * on-disk codec but not this v1 UI (use the JSON tab for that rare case).
 */
public final class MobIdentityTab implements MobCreatorTab {

    private static final int LABEL_DX = 8, FIELD_DX = 110, ROW_H = 24, BOX_H = 16;

    /** Strict datapack-path grammar — mirrors MobCreatorValidator.SAFE_ID_PATH. */
    private static final java.util.regex.Pattern SAFE_ID_PATH =
        java.util.regex.Pattern.compile("[a-z0-9_-]+(?:/[a-z0-9_-]+)*");

    private final LabeledField idPath = new LabeledField("id path");
    private final LabeledField name = new LabeledField("name");
    private final LabeledField description = new LabeledField("description");
    private final LabeledField icon = new LabeledField("icon");
    private final LabeledField targetType = new LabeledField("target entity");
    private final LabeledField targetTag = new LabeledField("target tag");
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();
    private final SearchPickerOverlay entityPicker = new SearchPickerOverlay();
    private final SearchPickerOverlay targetPicker = new SearchPickerOverlay();

    private MobOriginCreatorScreen parent;
    private int rowY;
    private Button targetTypeBtn;
    private Button targetTagBtn;
    /** Guard so that {@code setValue} from inside a responder doesn't recurse. */
    private boolean mutexSyncing;

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
        if (entityPicker.isOpen()) {
            int pw = Math.min(w - 20, 340), ph = h - 16;
            entityPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        if (targetPicker.isOpen()) {
            int pw = Math.min(w - 20, 340), ph = h - 16;
            targetPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
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

        // Target rows. Each row owns a dual-purpose button that opens the
        // picker when its field is empty and clears the field (re-enabling
        // the sibling) when it's populated. Mutual exclusion is also enforced
        // on direct typing via setResponder → applyMutex.
        parent.register(targetType.build(font, fx, rowY + ROW_H * 4, fieldW - 44, BOX_H));
        targetTypeBtn = Button.builder(Component.literal("pick"), b -> toggleTargetEntity())
            .bounds(fx + fieldW - 40, rowY + ROW_H * 4 - 2, 40, BOX_H + 4).build();
        parent.register(targetTypeBtn);

        parent.register(targetTag.build(font, fx, rowY + ROW_H * 5, fieldW - 44, BOX_H));
        targetTagBtn = Button.builder(Component.literal("pick"), b -> toggleTargetTag())
            .bounds(fx + fieldW - 40, rowY + ROW_H * 5 - 2, 40, BOX_H + 4).build();
        parent.register(targetTagBtn);

        targetType.setResponder(s -> onTargetChanged(true));
        targetTag.setResponder(s -> onTargetChanged(false));
        applyMutex();

        // Spawn-egg button (Phase 4d). Sits below the help line at the bottom
        // of the tab. Operates on the SAVED origin (id = neoorigins_custom:<idPath>),
        // so the user must Save first if the draft is new.
        int eggY = rowY + ROW_H * 7 + 8;
        parent.register(Button.builder(
                Component.literal("Give Spawn Egg"), b -> requestEgg())
            .bounds(fx, eggY, 120, BOX_H + 4).build());
    }

    private void toggleTargetEntity() {
        if (!targetType.value().trim().isEmpty()) {
            targetType.setValue(""); // responder → applyMutex re-enables the tag row
        } else {
            openTargetEntityPicker();
        }
    }

    private void toggleTargetTag() {
        if (!targetTag.value().trim().isEmpty()) {
            targetTag.setValue("");
        } else {
            openTargetTagPicker();
        }
    }

    private void openTargetEntityPicker() {
        pushToDraft();
        targetPicker.open("pick target entity",
            () -> BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(Object::toString).sorted().toList(),
            id -> {
                parent.draft().targetEntityType = id;
                parent.draft().targetEntityTag = "";
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void openTargetTagPicker() {
        pushToDraft();
        targetPicker.open("pick target tag",
            () -> BuiltInRegistries.ENTITY_TYPE.getTagNames()
                .map(tk -> tk.location().toString()).sorted().toList(),
            id -> {
                parent.draft().targetEntityTag = id;
                parent.draft().targetEntityType = "";
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    /** Called by the EditBox responder on either target row. The {@code isEntity}
     *  flag picks which sibling to clear when the just-edited row becomes
     *  non-blank. The {@link #mutexSyncing} guard prevents the recursive
     *  setValue("") from re-firing into this same handler. */
    private void onTargetChanged(boolean isEntity) {
        if (mutexSyncing) return;
        mutexSyncing = true;
        try {
            if (isEntity && !targetType.value().trim().isEmpty()) {
                targetTag.setValue("");
            } else if (!isEntity && !targetTag.value().trim().isEmpty()) {
                targetType.setValue("");
            }
            applyMutex();
        } finally {
            mutexSyncing = false;
        }
    }

    private void applyMutex() {
        boolean entityHas = !targetType.value().trim().isEmpty();
        boolean tagHas = !targetTag.value().trim().isEmpty();
        targetType.setEditable(!tagHas);
        targetTag.setEditable(!entityHas);
        if (targetTypeBtn != null) {
            targetTypeBtn.setMessage(Component.literal(entityHas ? "clear" : "pick"));
            // Disabled when the *other* row is set (so this row is locked empty).
            targetTypeBtn.active = !tagHas;
        }
        if (targetTagBtn != null) {
            targetTagBtn.setMessage(Component.literal(tagHas ? "clear" : "pick"));
            targetTagBtn.active = !entityHas;
        }
    }

    private void requestEgg() {
        pushToDraft();
        MobOriginDraft d = parent.draft();
        ResourceLocation originId = d.originId();
        if (!d.targetEntityType.isBlank()) {
            sendEggRequest(originId, "");
            return;
        }
        // Multi-type target (tag or list) — prompt for an entity type.
        entityPicker.open("pick entity type",
            () -> BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(Object::toString).sorted().toList(),
            id -> sendEggRequest(originId, id),
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void sendEggRequest(ResourceLocation originId, String entityTypeOverride) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.cyberday1.neoorigins.network.payload.RequestMobOriginEggPayload(
                originId.toString(), entityTypeOverride, 1));
    }

    private void openPicker() {
        pushToDraft();
        itemPicker.open(false,
            (id, comp) -> {
                ResourceLocation rl = ResourceLocation.tryParse(id);
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
        // Suppress the mutex responder during the bulk load so setting one
        // value doesn't clobber the other before both have been assigned.
        mutexSyncing = true;
        try {
            targetType.setValue(d.targetEntityType);
            targetTag.setValue(d.targetEntityTag);
        } finally {
            mutexSyncing = false;
        }
        applyMutex();
    }

    @Override
    public void pushToDraft() {
        if (itemPicker.isOpen()) return;
        MobOriginDraft d = parent.draft();
        d.idPath = idPath.value().trim();
        d.name = name.value();
        d.description = description.value();
        ResourceLocation ic = ResourceLocation.tryParse(icon.value().trim());
        if (ic != null) d.icon = ic;
        d.targetEntityType = targetType.value().trim();
        d.targetEntityTag = targetTag.value().trim();
    }

    @Override
    public void renderBackdrop(GuiGraphics g) {
        if (itemPicker.isOpen()) itemPicker.renderBackdrop(g);
        else if (entityPicker.isOpen()) entityPicker.renderBackdrop(g);
        else if (targetPicker.isOpen()) targetPicker.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (itemPicker.isOpen()) { itemPicker.render(g); return; }
        if (entityPicker.isOpen()) { entityPicker.render(g); return; }
        if (targetPicker.isOpen()) { targetPicker.render(g); return; }
        Font font = parent.font();
        int lx = x + LABEL_DX;
        CreatorStyle.sectionHeader(g, font, "Mob origin basics", lx, y, w - LABEL_DX * 2);
        idPath.drawLabel(g, font, lx, rowY + 4);
        name.drawLabel(g, font, lx, rowY + ROW_H + 4);
        description.drawLabel(g, font, lx, rowY + ROW_H * 2 + 4);
        icon.drawLabel(g, font, lx, rowY + ROW_H * 3 + 4);
        targetType.drawLabel(g, font, lx, rowY + ROW_H * 4 + 4);
        targetTag.drawLabel(g, font, lx, rowY + ROW_H * 5 + 4);
        g.drawString(font, "Set ONE of: target entity (e.g. minecraft:zombie) "
                + "or target tag (e.g. minecraft:undead).",
            lx, rowY + ROW_H * 6 + 6, CreatorStyle.TEXT_DIM, false);

        // Inline validation, live from the widgets: compact "✕ reason" to the
        // right of the offending box (glyph prefix → not color-only). Same
        // rules MobCreatorValidator enforces at Save, surfaced at type time.
        int fieldW = Math.min(w - FIELD_DX - 8, 240);
        int errX = x + FIELD_DX + fieldW + 6;
        String id = idPath.value().trim();
        String idErr = id.isEmpty() ? "required"
            : !SAFE_ID_PATH.matcher(id).matches() ? "a-z 0-9 _ - / only" : null;
        if (idErr != null) {
            g.drawString(font, "✕ " + idErr, errX, rowY + 4, CreatorStyle.ERR, false);
        }
        if (ResourceLocation.tryParse(icon.value().trim()) == null) {
            g.drawString(font, "✕ invalid item id", errX, rowY + ROW_H * 3 + 4,
                CreatorStyle.ERR, false);
        }
        boolean hasTarget = !targetType.value().trim().isEmpty()
            || !targetTag.value().trim().isEmpty();
        if (!hasTarget) {
            g.drawString(font, "✕ set a target", errX, rowY + ROW_H * 4 + 4,
                CreatorStyle.ERR, false);
        } else {
            String tv = targetType.value().trim();
            String gv = targetTag.value().trim();
            if (!tv.isEmpty() && ResourceLocation.tryParse(tv) == null) {
                g.drawString(font, "✕ invalid entity id", errX, rowY + ROW_H * 4 + 4,
                    CreatorStyle.ERR, false);
            }
            if (!gv.isEmpty() && ResourceLocation.tryParse(
                    gv.startsWith("#") ? gv.substring(1) : gv) == null) {
                g.drawString(font, "✕ invalid tag id", errX, rowY + ROW_H * 5 + 4,
                    CreatorStyle.ERR, false);
            }
        }

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
        if (itemPicker.isOpen())   return itemPicker.onScroll(mx, my, sy);
        if (entityPicker.isOpen()) return entityPicker.onScroll(mx, my, sy);
        if (targetPicker.isOpen()) return targetPicker.onScroll(mx, my, sy);
        return false;
    }
}
