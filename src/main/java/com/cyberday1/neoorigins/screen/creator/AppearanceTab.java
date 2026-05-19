package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.PowerFormPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Appearance tab — guided editor for the Tier-A visual powers. A chooser cycles
 * the six visual concepts; the selected one is found in (or added to) the
 * draft's power list and edited through the shared {@link PowerFormPanel}.
 * Overlay/shader carry an asset-path hint telling the author which resource-pack
 * file they must ship (geometry/skins remain client assets, out of 2.1 scope).
 *
 * <p>{@code invisibility}/{@code glow} are not their own power types — they are
 * {@code persistent_effect} presets, matched back by their effect id so the
 * chooser re-finds the right entry.
 */
public final class AppearanceTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.appearance");

    private static final int HDR_H = 20, LABEL_DX = 6, FIELD_DX = 140;

    /** label · power type · preset body · asset hint (null = none) · effect id
     *  used to disambiguate persistent_effect presets (null = match by type). */
    private record Visual(String label, String typeId, String preset,
                          String assetHint, String matchEffect) {}

    private static final List<Visual> VISUALS = List.of(
        new Visual("overlay", "neoorigins:overlay", "{}",
            "ship texture at: assets/neoorigins_custom/textures/<your_overlay>.png", null),
        new Visual("model_color", "neoorigins:model_color", "{}", null, null),
        new Visual("shader", "neoorigins:shader", "{}",
            "ship shader at: assets/neoorigins_custom/shaders/post/<your_shader>.json", null),
        new Visual("size_scaling", "neoorigins:size_scaling", "{}", null, null),
        new Visual("invisibility", "neoorigins:persistent_effect",
            "{\"effect\":\"minecraft:invisibility\",\"amplifier\":0}", null,
            "minecraft:invisibility"),
        new Visual("glow", "neoorigins:persistent_effect",
            "{\"effect\":\"minecraft:glowing\",\"amplifier\":0}", null,
            "minecraft:glowing"));

    private final PowerFormPanel form = new PowerFormPanel();

    private OriginCreatorScreen parent;
    private int x, y, w, h;
    private int visIdx;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Optional visual powers (overlay, color, shader, size, invisibility, glow).");
    }

    private Visual visual() { return VISUALS.get(visIdx); }

    private List<PowerDraft> powers() { return parent.draft().powers; }

    /** The draft power matching the selected visual, or {@code null}. */
    private PowerDraft match() {
        Visual v = visual();
        for (PowerDraft p : powers()) {
            if (!v.typeId().equals(p.typeId)) continue;
            if (v.matchEffect() == null
                    || (p.rawJson != null && p.rawJson.contains("\"" + v.matchEffect() + "\""))) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;

        parent.register(Button.builder(
                Component.literal("◀ " + visual().label() + " ▶"), b -> cycleVisual())
            .bounds(x + LABEL_DX, y, 150, HDR_H).build());

        PowerDraft p = match();
        if (p == null) {
            parent.register(Button.builder(
                    Component.literal("+ add " + visual().label()), b -> addVisual())
                .bounds(x + LABEL_DX + 158, y, 110, HDR_H).build());
            form.init(parent, null, false, x, y, w, 0);
            return;
        }
        parent.register(Button.builder(Component.literal("- remove"), b -> removeVisual())
            .bounds(x + LABEL_DX + 158, y, 90, HDR_H).build());

        int formTop = y + HDR_H + (visual().assetHint() != null ? 24 : 14);
        form.init(parent, p, false, x, formTop, w, (y + h) - formTop);
    }

    private void cycleVisual() {
        pushToDraft();
        visIdx = (visIdx + 1) % VISUALS.size();
        parent.requestRebuild();
    }

    private void addVisual() {
        pushToDraft();
        Visual v = visual();
        PowerDraft np = new PowerDraft(null, v.typeId());
        np.rawJson = v.preset();
        np.powerId = parent.draft().mintPowerId(np, v.typeId());
        powers().add(np);
        parent.requestRebuild();
    }

    private void removeVisual() {
        PowerDraft p = match();
        if (p != null) powers().remove(p);
        parent.requestRebuild();
    }

    @Override public void pullFromDraft() { form.pull(); }

    @Override
    public void pushToDraft() {
        PowerDraft p = match();
        if (p != null) {
            p.powerId = parent.draft().mintPowerId(p, p.typeId);
            form.push();
        }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        PowerDraft p = match();

        if (p == null) {
            g.text(font, "not added to this origin yet",
                x + LABEL_DX, y + HDR_H + 8, CreatorStyle.TEXT_DIM, false);
            return;
        }
        if (p.powerId != null) {
            g.text(font, "id: " + p.powerId,
                x + LABEL_DX, y + HDR_H + 2, CreatorStyle.TEXT_DIM, false);
        }
        String hint = visual().assetHint();
        if (hint != null) {
            g.text(font, hint, x + LABEL_DX, y + HDR_H + 12, CreatorStyle.HINT, false);
        }
        form.render(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return form.onScroll(mx, my, sy);
    }
}
