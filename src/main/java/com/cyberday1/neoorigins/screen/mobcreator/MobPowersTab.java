package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.PowerFormPanel;
import com.cyberday1.neoorigins.screen.creator.widget.SearchPickerOverlay;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Powers tab for mob origins — identical behavior to the player
 * {@code PowersTab} (powers are the same {@link PowerDraft}); reuses the
 * shared {@link PowerFormPanel} + {@link SearchPickerOverlay} via the
 * {@code CreatorHost} seam. Bound to {@link MobOriginCreatorScreen} /
 * {@code MobOriginDraft} instead of the player draft.
 */
public final class MobPowersTab implements MobCreatorTab {

    private static final int HDR_H = 20, LABEL_DX = 6;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PowerFormPanel form = new PowerFormPanel();
    private final SearchPickerOverlay typePicker = new SearchPickerOverlay();
    private final SearchPickerOverlay importPicker = new SearchPickerOverlay();

    private MobOriginCreatorScreen parent;
    private int x, y, w, h;
    private int sel = 0;
    private boolean rawMode;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.powers");
    }
    @Override public Component help() {
        return Component.literal("Powers applied to the mob. Pick a type, fill fields, or edit JSON.");
    }

    private List<PowerDraft> powers() { return parent.draft().powers; }

    private PowerDraft current() {
        List<PowerDraft> p = powers();
        return (sel >= 0 && sel < p.size()) ? p.get(sel) : null;
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;

        if (typePicker.isOpen()) {
            int pw = Math.min(w - 20, 320), ph = h - 16;
            typePicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        if (importPicker.isOpen()) {
            int pw = Math.min(w - 20, 320), ph = h - 16;
            importPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        if (form.overlayOpen()) {
            form.init(parent, current(), rawMode, x, y, w, h);
            return;
        }

        List<PowerDraft> ps = powers();
        if (sel >= ps.size()) sel = Math.max(0, ps.size() - 1);

        int bx = x + LABEL_DX, by = y;
        parent.register(Button.builder(Component.literal("<"), b -> step(-1))
            .bounds(bx, by, 18, HDR_H).build());
        parent.register(Button.builder(Component.literal(">"), b -> step(1))
            .bounds(bx + 20, by, 18, HDR_H).build());
        parent.register(Button.builder(Component.literal("+ add"), b -> addPower())
            .bounds(bx + 42, by, 44, HDR_H).build());
        parent.register(Button.builder(Component.literal("import"), b -> importPower())
            .bounds(bx + 88, by, 50, HDR_H).build());
        parent.register(Button.builder(Component.literal("- del"), b -> removePower())
            .bounds(bx + 140, by, 44, HDR_H).build());

        PowerDraft p = current();
        if (p == null) return;

        int r2 = y + HDR_H + 4;
        parent.register(Button.builder(
                Component.literal("Type: " + shortType(p.typeId) + "  (change)"),
                b -> openTypePicker())
            .bounds(x + LABEL_DX, r2, w - 124, HDR_H).build());
        parent.register(Button.builder(
                Component.literal(rawMode ? "Switch to form" : "Switch to JSON"),
                b -> toggleRaw())
            .bounds(x + w - 110, r2, 104, HDR_H).build());

        int formTop = y + HDR_H * 2 + 26;
        form.init(parent, p, rawMode, x, formTop, w, (y + h) - formTop);
    }

    private void step(int d) {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        pushToDraft();
        sel = Math.floorMod(sel + d, ps.size());
        parent.requestRebuild();
    }

    private void addPower() {
        pushToDraft();
        typePicker.open("pick a power type", FormModel::creatorTypes,
            type -> {
                PowerDraft np = new PowerDraft(null, type);
                np.rawJson = "{}";
                powers().add(np);
                sel = powers().size() - 1;
                rawMode = false;
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    /** "import" browses every loaded built-in power and drops an editable copy. */
    private void importPower() {
        pushToDraft();
        importPicker.open("import a power",
            () -> {
                List<String> ids = new ArrayList<>();
                for (Identifier id : PowerDataManager.INSTANCE.getAllPowers().keySet()) {
                    ids.add(id.toString());
                }
                ids.sort(null);
                return ids;
            },
            this::importPickedPower,
            parent::requestRebuild);
        parent.requestRebuild();
    }

    /** Deep-copy the picked built-in power's body into a fresh editable draft. */
    private void importPickedPower(String idString) {
        Identifier pid;
        try {
            pid = Identifier.parse(idString);
        } catch (RuntimeException e) {
            LOGGER.warn("[neoorigins] cannot import power with invalid id '{}'", idString);
            return;
        }
        JsonObject body = PowerDataManager.INSTANCE.getRawPowerJson(pid);
        if (body == null) {
            LOGGER.warn("[neoorigins] cannot import power '{}': no raw body available", idString);
            return;
        }
        String type = body.has("type") ? body.get("type").getAsString() : "";
        body.remove("type");

        PowerDraft np = new PowerDraft(null, type);
        np.rawJson = body.toString();
        powers().add(np);
        sel = powers().size() - 1;
        rawMode = false;
    }

    private void removePower() {
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) return;
        ps.remove(sel);
        sel = Math.max(0, Math.min(sel, ps.size() - 1));
        parent.requestRebuild();
    }

    private void openTypePicker() {
        PowerDraft p = current();
        if (p == null) return;
        pushToDraft();
        typePicker.open("pick a power type", FormModel::creatorTypes,
            type -> {
                PowerDraft cur = current();
                if (cur != null && !type.equals(cur.typeId)) {
                    cur.typeId = type;
                    cur.rawJson = "{}";
                }
            },
            parent::requestRebuild);
        parent.requestRebuild();
    }

    private void toggleRaw() {
        if (current() == null) return;
        pushToDraft();
        rawMode = !rawMode;
        parent.requestRebuild();
    }

    @Override public void pullFromDraft() {
        if (!typePicker.isOpen() && !importPicker.isOpen()) form.pull();
    }

    @Override
    public void pushToDraft() {
        if (typePicker.isOpen() || importPicker.isOpen()) return;
        PowerDraft p = current();
        if (p == null) return;
        p.powerId = parent.draft().mintPowerId(p, p.typeId);
        form.push();
    }

    private static String shortType(String typeId) {
        int c = typeId.indexOf(':');
        return c >= 0 ? typeId.substring(c + 1) : typeId;
    }

    @Override
    public void renderBackdrop(GuiGraphicsExtractor g) {
        if (typePicker.isOpen()) typePicker.renderBackdrop(g);
        else if (importPicker.isOpen()) importPicker.renderBackdrop(g);
        else if (form.overlayOpen()) form.refBackdrop(g);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (typePicker.isOpen()) { typePicker.render(g); return; }
        if (importPicker.isOpen()) { importPicker.render(g); return; }
        if (form.overlayOpen()) return;

        Font font = parent.font();
        List<PowerDraft> ps = powers();
        if (ps.isEmpty()) {
            CreatorStyle.emptyState(g, font,
                "No powers yet — click \"+ add\" to choose one", x + w / 2, y + h / 2 - 4);
            return;
        }
        g.text(font, "Power " + (sel + 1) + " / " + ps.size(),
            x + 140, y + 6, CreatorStyle.TEXT_DIM, false);
        PowerDraft p = current();
        int idY = y + HDR_H * 2 + 8;
        if (p != null && p.powerId != null) {
            g.text(font, "id: " + p.powerId + "    "
                    + (rawMode ? "(editing JSON)" : "(editing form)"),
                x + LABEL_DX, idY, CreatorStyle.TEXT_DIM, false);
        }
        // Live field-problem counter (mirrors PowersTab) — advisory; the Save
        // button's gate is the real block.
        int errs = form.errorCount();
        if (errs > 0) {
            String e = "✕ " + errs + " field problem" + (errs == 1 ? "" : "s");
            g.text(font, e, x + w - font.width(e) - 8, idY, CreatorStyle.ERR, false);
        }
        CreatorStyle.divider(g, x + 4, idY + 12, w - 8);
        form.render(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (typePicker.isOpen()) return typePicker.onScroll(mx, my, sy);
        if (importPicker.isOpen()) return importPicker.onScroll(mx, my, sy);
        if (form.overlayOpen()) return form.refScroll(mx, my, sy);
        return form.onScroll(mx, my, sy);
    }
}
