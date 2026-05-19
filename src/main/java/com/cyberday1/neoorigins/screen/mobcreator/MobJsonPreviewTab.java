package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.widget.ScrollPanel;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import com.cyberday1.neoorigins.service.MobCustomPackSerializer;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only preview of exactly what Save will write for the mob origin
 * (the {@code origins/mob_origins/<id>.json} body + each power file). No layer
 * patch (mobs aren't layered). The authoritative validation is server-side
 * ({@code MobCreatorValidator}); this tab is the "what will be written" mirror.
 */
public final class MobJsonPreviewTab implements MobCreatorTab {

    private static final int LINE_H = 10;
    private static final com.google.gson.Gson PRETTY =
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private MobOriginCreatorScreen parent;
    private final ScrollPanel scroll = new ScrollPanel();
    private final List<String> lines = new ArrayList<>();
    private int x, y, w, h;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.json");
    }
    @Override public Component help() {
        return Component.literal("Read-only preview of the files Save will write.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x; this.y = y; this.w = w; this.h = h;
        scroll.setViewport(x, y, w, h);
        rebuildLines();
    }

    @Override public void pullFromDraft() { rebuildLines(); }

    private void rebuildLines() {
        lines.clear();
        MobOriginDraft d = parent.draft();
        try {
            Identifier originId = d.originId();
            section("data/" + originId.getNamespace() + "/origins/mob_origins/"
                + originId.getPath() + ".json");
            JsonObject body = MobCustomPackSerializer.mobOriginJson(d);
            body.addProperty("id", originId.toString());
            emit(body);
            for (OriginDraft.PowerDraft p : d.powers) {
                String path = p.powerId != null ? p.powerId.getPath() : "(unsaved)";
                section("data/" + OriginDraft.CUSTOM_NAMESPACE
                    + "/origins/powers/" + path + ".json");
                emit(MobCustomPackSerializer.powerJson(p));
            }
        } catch (RuntimeException e) {
            section("(preview unavailable — check the Identity tab)");
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
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        scroll.beginClip(g);
        int top = scroll.contentTop();
        for (int i = 0; i < lines.size(); i++) {
            int ly = top + i * LINE_H;
            if (ly + LINE_H < scroll.viewTop() || ly > scroll.viewBottom()) continue;
            String line = lines.get(i);
            int color = line.startsWith("# ") ? CreatorStyle.HINT : 0xFFC8C8D8;
            g.text(font, line, x + 4, ly, color, false);
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
