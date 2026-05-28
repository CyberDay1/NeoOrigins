package com.cyberday1.neoorigins.screen.creator;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.cyberday1.neoorigins.screen.creator.widget.ScrollPanel;
import com.cyberday1.neoorigins.service.CustomPackSerializer;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Live read-only preview of exactly what the datapack writer will emit for the
 * current draft — the origin JSON (with the synthetic id injected as
 * {@code OriginDataManager} does), each power file, and the layer patch —
 * pretty-printed and vertically scrollable. Authoring still happens in the
 * other tabs (per-power raw editing is the Powers/Appearance escape hatch);
 * this tab is the "what will be written" mirror.
 */
public final class JsonPreviewTab implements CreatorTab {

    private static final Component TITLE =
        Component.translatable("gui.neoorigins.creator.tab.json");
    private static final int LINE_H = 10;
    private static final com.google.gson.Gson PRETTY =
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private OriginCreatorScreen parent;
    private final ScrollPanel scroll = new ScrollPanel();
    private final List<String> lines = new ArrayList<>();
    private int x, y, w, h;

    @Override public Component title() { return TITLE; }
    @Override public Component help() {
        return Component.literal(
            "Problems check + read-only preview of the files Save will write.");
    }

    @Override
    public void init(OriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        scroll.setViewport(x, y, w, h);
        rebuildLines();
    }

    @Override public void pullFromDraft() { rebuildLines(); }

    private void rebuildLines() {
        lines.clear();
        OriginDraft d = parent.draft();

        // Sanity panel first — same checks the server gate runs on Save.
        // Use the client connection's registry access so modded/datapack ids
        // resolve the same way the server validator sees them.
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        net.minecraft.core.RegistryAccess ra = conn != null
            ? conn.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
        java.util.List<String> probs =
            com.cyberday1.neoorigins.service.DraftSanity.draftProblems(ra, d);
        if (probs.isEmpty()) {
            lines.add("> No problems detected — safe to Save.");
        } else {
            lines.add("# Problems (" + probs.size() + ") — fix before Save:");
            for (String pr : probs) lines.add("! - " + pr);
        }

        try {
            ResourceLocation originId = d.originId();
            section("data/" + originId.getNamespace() + "/origins/origins/"
                + originId.getPath() + ".json");
            JsonObject originJson = CustomPackSerializer.originJson(d);
            originJson.addProperty("id", originId.toString());
            emit(originJson);

            for (PowerDraft p : d.powers) {
                String path = p.powerId != null ? p.powerId.getPath() : "(unsaved)";
                section("data/" + OriginDraft.CUSTOM_NAMESPACE
                    + "/origins/powers/" + path + ".json");
                emit(CustomPackSerializer.powerJson(p));
            }

            section("layer patch → " + d.layerId.toString()
                + " (merged into the canonical picker)");
            emit(CustomPackSerializer.layerPatch(null, originId.toString()));
        } catch (RuntimeException e) {
            section("(preview unavailable — fix the problems above)");
        }

        scroll.setContentHeight(lines.size() * LINE_H + 4);
    }

    private void section(String title) {
        if (!lines.isEmpty()) lines.add("");
        lines.add("# " + title);
    }

    private void emit(JsonObject json) {
        for (String l : PRETTY.toJson(json).split("\n")) lines.add(l);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        scroll.beginClip(g);
        int top = scroll.contentTop();
        for (int i = 0; i < lines.size(); i++) {
            int ly = top + i * LINE_H;
            if (ly + LINE_H < scroll.viewTop() || ly > scroll.viewBottom()) continue;
            String line = lines.get(i);
            int color = line.startsWith("! ") ? CreatorStyle.ERR
                : line.startsWith("# ") ? CreatorStyle.HINT
                : line.startsWith("> ") ? CreatorStyle.OK
                : 0xFFC8C8D8;
            g.drawString(font, line, x + 4, ly, color, false);
        }
        scroll.endClip(g);
        scroll.renderScrollbar(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < x || mx > x + w || my < scroll.viewTop() || my > scroll.viewBottom()) {
            return false;
        }
        return scroll.onScroll(sy);
    }
}
