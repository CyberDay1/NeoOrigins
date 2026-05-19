package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.PowerFormPanel;
import com.cyberday1.neoorigins.screen.creator.widget.SearchPickerOverlay;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Appearance tab — guided editor for the Tier-A visual powers plus a "what's
 * actually available" reference and Browse pickers backed by the live registry
 * / resource stack ({@link CreatorAssets}): pick a real installed status
 * effect, texture, or post-shader instead of guessing a path.
 *
 * <p>{@code invisibility}/{@code glow} are {@code persistent_effect} presets,
 * matched back by their effect id so the chooser re-finds the right entry.
 */
public final class AppearanceTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.appearance");

    private static final int HDR_H = 20, LABEL_DX = 6;

    private enum Browse { NONE, EFFECT, TEXTURE, SHADER }

    /** label · type · preset · asset hint · effect-match · browsable field · kind. */
    private record Visual(String label, String typeId, String preset, String assetHint,
                          String matchEffect, String browseField, Browse browse) {}

    private static final List<Visual> VISUALS = List.of(
        new Visual("overlay", "neoorigins:overlay", "{}",
            "ship texture at: assets/neoorigins_custom/textures/<your_overlay>.png",
            null, "texture", Browse.TEXTURE),
        new Visual("model_color", "neoorigins:model_color", "{}", null,
            null, null, Browse.NONE),
        new Visual("shader", "neoorigins:shader", "{}",
            "ship shader at: assets/neoorigins_custom/shaders/post/<your_shader>.json",
            null, "shader", Browse.SHADER),
        new Visual("size_scaling", "neoorigins:size_scaling", "{}", null,
            null, null, Browse.NONE),
        new Visual("invisibility", "neoorigins:persistent_effect",
            "{\"effect\":\"minecraft:invisibility\",\"amplifier\":0}", null,
            "minecraft:invisibility", "effect", Browse.EFFECT),
        new Visual("glow", "neoorigins:persistent_effect",
            "{\"effect\":\"minecraft:glowing\",\"amplifier\":0}", null,
            "minecraft:glowing", "effect", Browse.EFFECT));

    private static final java.util.Map<String, String> DESC = java.util.Map.of(
        "overlay", "Full-screen texture over the player's view (tint, vignette…).",
        "model_color", "Tints the player model. RGBA only — no asset needed.",
        "shader", "A post-process screen shader while the origin is active.",
        "size_scaling", "Scales the player up or down. Numeric only.",
        "invisibility", "Permanent invisibility (vanilla minecraft:invisibility).",
        "glow", "Permanent glowing outline (vanilla minecraft:glowing).");

    private final PowerFormPanel form = new PowerFormPanel();
    private final SearchPickerOverlay assetPicker = new SearchPickerOverlay();

    private OriginCreatorScreen parent;
    private int x, y, w, h;
    private int visIdx;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Optional visual powers — Browse picks real installed effects/textures/shaders.");
    }

    private Visual visual() { return VISUALS.get(visIdx); }

    private List<PowerDraft> powers() { return parent.draft().powers; }

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

        if (assetPicker.isOpen()) {
            int pw = Math.min(w - 20, 360), ph = h - 16;
            assetPicker.build(parent, x + (w - pw) / 2, y + 8, pw, ph);
            return;
        }
        if (form.overlayOpen()) {            // REF condition/action picker
            form.init(parent, match(), false, x, y, w, h);
            return;
        }

        parent.register(Button.builder(
                Component.literal("◀ " + CreatorStyle.title(visual().label()) + " ▶"),
                b -> cycleVisual())
            .bounds(x + LABEL_DX, y, 150, HDR_H).build());

        PowerDraft p = match();
        if (p == null) {
            parent.register(Button.builder(
                    Component.literal("+ add " + CreatorStyle.title(visual().label())),
                    b -> addVisual())
                .bounds(x + LABEL_DX + 158, y, 110, HDR_H).build());
            form.init(parent, null, false, x, y, w, 0);
            return;
        }
        parent.register(Button.builder(Component.literal("- remove"), b -> removeVisual())
            .bounds(x + LABEL_DX + 158, y, 84, HDR_H).build());
        if (visual().browse() != Browse.NONE) {
            parent.register(Button.builder(
                    Component.literal("Browse " + visual().browseField()), b -> openBrowse())
                .bounds(x + LABEL_DX + 246, y, 120, HDR_H).build());
        }

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

    private void openBrowse() {
        Visual v = visual();
        PowerDraft p = match();
        if (p == null || v.browse() == Browse.NONE) return;
        pushToDraft();
        Supplier<List<String>> source = switch (v.browse()) {
            case EFFECT  -> CreatorAssets::effectIds;
            case TEXTURE -> CreatorAssets::textureAssets;
            case SHADER  -> CreatorAssets::shaderAssets;
            default      -> List::of;
        };
        assetPicker.open("pick " + v.browseField(), source,
            value -> setField(v.browseField(), value),
            parent::requestRebuild);
        parent.requestRebuild();
    }

    /** Write {@code field=value} into the matched power's raw config JSON. */
    private void setField(String field, String value) {
        PowerDraft p = match();
        if (p == null) return;
        JsonObject body;
        try {
            JsonElement el = JsonParser.parseString(
                p.rawJson == null || p.rawJson.isBlank() ? "{}" : p.rawJson);
            body = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            body = new JsonObject();
        }
        body.addProperty(field, value);
        p.rawJson = body.toString();
    }

    @Override public void pullFromDraft() { if (!assetPicker.isOpen()) form.pull(); }

    @Override
    public void pushToDraft() {
        if (assetPicker.isOpen()) return;
        PowerDraft p = match();
        if (p != null) {
            p.powerId = parent.draft().mintPowerId(p, p.typeId);
            form.push();
        }
    }

    @Override
    public void renderBackdrop(GuiGraphicsExtractor g) {
        if (assetPicker.isOpen()) assetPicker.renderBackdrop(g);
        else if (form.overlayOpen()) form.refBackdrop(g);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        if (assetPicker.isOpen() || form.overlayOpen()) return; // backdrop draws it
        Font font = parent.font();
        PowerDraft p = match();

        Visual v = visual();
        if (p == null) {
            int hy = y + HDR_H + 10;
            CreatorStyle.sectionHeader(g, font, CreatorStyle.title(v.label()),
                x + LABEL_DX, hy,
                w - LABEL_DX * 2);
            g.text(font, DESC.getOrDefault(v.label(), ""),
                x + LABEL_DX, hy + 16, CreatorStyle.TEXT_DIM, false);
            if (v.assetHint() != null) {
                g.text(font, v.assetHint(), x + LABEL_DX, hy + 30,
                    CreatorStyle.HINT, false);
            }
            g.text(font,
                "Not added yet — click \"+ add " + CreatorStyle.title(v.label()) + "\".",
                x + LABEL_DX, hy + 46, CreatorStyle.TEXT_DIM, false);
            return;
        }
        if (p.powerId != null) {
            g.text(font, "id: " + p.powerId,
                x + LABEL_DX, y + HDR_H + 2, CreatorStyle.TEXT_DIM, false);
        }
        if (v.assetHint() != null) {
            g.text(font, v.assetHint(), x + LABEL_DX, y + HDR_H + 12,
                CreatorStyle.HINT, false);
        }
        form.render(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (assetPicker.isOpen()) return assetPicker.onScroll(mx, my, sy);
        if (form.overlayOpen()) return form.refScroll(mx, my, sy);
        return form.onScroll(mx, my, sy);
    }
}
