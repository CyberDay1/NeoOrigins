package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.cyberday1.neoorigins.screen.creator.widget.ScrollPanel;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn-rules tab — edits {@code SpawnRules} on the draft. Two sections,
 * both inside a {@link ScrollPanel} so the whole tab fits on small windows:
 *
 * <ol>
 *   <li>Spawn rules: enable / weight / time / Y range / light range /
 *       spawn reasons grid / mutex / replace</li>
 *   <li>Location filter: dimension / biome / biome_tag / biomes (csv) /
 *       structure / structure_tag / allow water surface / allow ocean floor /
 *       min Y / max Y / can_see_sky</li>
 * </ol>
 *
 * <p>The {@code spawn_reasons} list is hard-coded rather than reflected off
 * the running enum — its values differ between {@code MobSpawnType} (1.21.1)
 * and {@code EntitySpawnReason} (26.1). The codec rejects names not in the
 * running enum, so the validator catches a bad pick at Save time.
 *
 * <p>Each widget has a fixed logical Y (offset within the scroll content);
 * {@link #layout} translates that to a screen Y via {@code scroll.contentTop()}
 * and toggles {@link AbstractWidget#visible} so off-viewport widgets don't
 * receive clicks. Labels are drawn in {@link #render} the same way.
 */
public final class MobSpawnRulesTab implements MobCreatorTab {

    private static final List<String> TIME_OF_DAY = List.of("any", "day", "night");
    private static final List<String> YES_NO = List.of("no", "yes");
    private static final List<String> TRISTATE = List.of("any", "true", "false");
    private static final List<String> SPAWN_REASONS = List.of(
        "natural", "spawner", "chunk_generation", "breeding",
        "reinforcement", "event", "spawn_egg", "command",
        "structure", "bucket", "dispenser", "mob_summoned",
        "patrol", "conversion", "jockey", "triggered");

    private static final int ROW_H = 22, BOX_H = 14;
    private static final int Y_RULES_HEADER     = 0;
    private static final int Y_ENABLED          = 14;
    private static final int Y_WEIGHT_TIME      = Y_ENABLED       + ROW_H;
    private static final int Y_YRANGE           = Y_WEIGHT_TIME   + ROW_H;
    private static final int Y_LIGHTRANGE       = Y_YRANGE        + ROW_H;
    private static final int Y_REASONS_HEADER   = Y_LIGHTRANGE    + ROW_H;
    private static final int Y_REASONS_GRID     = Y_REASONS_HEADER + 14;
    private static final int Y_AFTER_GRID       = Y_REASONS_GRID  + 4 * 18 + 4;
    private static final int Y_MUTEX            = Y_AFTER_GRID;
    private static final int Y_REPLACE          = Y_MUTEX         + ROW_H;
    private static final int Y_LOCATION_HEADER  = Y_REPLACE       + ROW_H + 8;
    private static final int Y_DIMENSION        = Y_LOCATION_HEADER + 14;
    private static final int Y_BIOME_TAG        = Y_DIMENSION     + ROW_H;
    private static final int Y_BIOMES_CSV       = Y_BIOME_TAG     + ROW_H;
    private static final int Y_STRUCTURE        = Y_BIOMES_CSV    + ROW_H;
    private static final int Y_WATER_FLOOR      = Y_STRUCTURE     + ROW_H;
    private static final int Y_MINY_MAXY        = Y_WATER_FLOOR   + ROW_H;
    private static final int Y_CANSEESKY        = Y_MINY_MAXY     + ROW_H;
    private static final int CONTENT_H          = Y_CANSEESKY     + ROW_H + 8;

    private final CycleSelector<String> enabled = new CycleSelector<>(YES_NO, s -> s);
    private final LabeledField weight = new LabeledField("weight", LabeledField.doubleFilter());
    private final CycleSelector<String> timeOfDay = new CycleSelector<>(TIME_OF_DAY, s -> s);
    private final CycleSelector<String> yRangeOn = new CycleSelector<>(YES_NO, s -> s);
    private final LabeledField yMin = new LabeledField("y min", LabeledField.intFilter());
    private final LabeledField yMax = new LabeledField("y max", LabeledField.intFilter());
    private final CycleSelector<String> lightRangeOn = new CycleSelector<>(YES_NO, s -> s);
    private final LabeledField lightMin = new LabeledField("light min", LabeledField.intFilter());
    private final LabeledField lightMax = new LabeledField("light max", LabeledField.intFilter());
    private final LabeledField mutexGroup = new LabeledField("mutex group");
    private final CycleSelector<String> replace = new CycleSelector<>(YES_NO, s -> s);
    private final Map<String, Button> reasonButtons = new LinkedHashMap<>();

    private final LabeledField dimension = new LabeledField("dimension");
    private final LabeledField biome = new LabeledField("biome");
    private final LabeledField biomeTag = new LabeledField("biome tag");
    private final LabeledField biomesCsv = new LabeledField("biomes csv");
    private final LabeledField structure = new LabeledField("structure");
    private final LabeledField structureTag = new LabeledField("structure tag");
    private final CycleSelector<String> allowWaterSurface = new CycleSelector<>(YES_NO, s -> s);
    private final CycleSelector<String> allowOceanFloor = new CycleSelector<>(YES_NO, s -> s);
    private final CycleSelector<String> minYOn = new CycleSelector<>(YES_NO, s -> s);
    private final LabeledField minY = new LabeledField("min y", LabeledField.intFilter());
    private final CycleSelector<String> maxYOn = new CycleSelector<>(YES_NO, s -> s);
    private final LabeledField maxY = new LabeledField("max y", LabeledField.intFilter());
    private final CycleSelector<String> canSeeSky = new CycleSelector<>(TRISTATE, s -> s);

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<Placement> placements = new ArrayList<>();

    private MobOriginCreatorScreen parent;
    private int contentX, contentW;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.spawn_rules");
    }
    @Override public Component help() {
        return Component.literal("When (and how often) this origin rolls onto a freshly-spawned mob.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.contentX = x;
        this.contentW = w;
        reasonButtons.clear();
        placements.clear();
        scroll.setViewport(x, y, w, h);
        scroll.setContentHeight(CONTENT_H);

        Font font = parent.font();
        int labelDx = 8;
        int col1 = labelDx + 90;
        int col2 = w / 2 + 90;

        place(enabled.build(0, 0, 64, BOX_H), col1, Y_ENABLED + 2);

        place(weight.build(font, 0, 0, 80, BOX_H), col1, Y_WEIGHT_TIME + 2);
        place(timeOfDay.build(0, 0, 80, BOX_H), col2, Y_WEIGHT_TIME + 2);

        place(yRangeOn.build(0, 0, 48, BOX_H), col1, Y_YRANGE + 2);
        place(yMin.build(font, 0, 0, 56, BOX_H), col1 + 56, Y_YRANGE + 2);
        place(yMax.build(font, 0, 0, 56, BOX_H), col1 + 56 + 64, Y_YRANGE + 2);

        place(lightRangeOn.build(0, 0, 48, BOX_H), col1, Y_LIGHTRANGE + 2);
        place(lightMin.build(font, 0, 0, 56, BOX_H), col1 + 56, Y_LIGHTRANGE + 2);
        place(lightMax.build(font, 0, 0, 56, BOX_H), col1 + 56 + 64, Y_LIGHTRANGE + 2);

        int reasonsX = labelDx;
        int colW = (w - labelDx * 2) / 4;
        for (int i = 0; i < SPAWN_REASONS.size(); i++) {
            int row = i / 4, col = i % 4;
            String reason = SPAWN_REASONS.get(i);
            Button b = Button.builder(reasonLabel(reason),
                btn -> toggleReason(reason, btn))
                .bounds(0, 0, colW - 4, 16).build();
            reasonButtons.put(reason, b);
            place(b, reasonsX + col * colW, Y_REASONS_GRID + row * 18);
        }

        place(mutexGroup.build(font, 0, 0, 140, BOX_H), col1, Y_MUTEX + 2);
        place(replace.build(0, 0, 64, BOX_H), col1, Y_REPLACE + 2);

        place(dimension.build(font, 0, 0, 200, BOX_H), col1, Y_DIMENSION + 2);
        place(biome.build(font, 0, 0, 130, BOX_H), col1, Y_BIOME_TAG + 2);
        place(biomeTag.build(font, 0, 0, 130, BOX_H), col2, Y_BIOME_TAG + 2);
        place(biomesCsv.build(font, 0, 0, w - col1 - labelDx - 8, BOX_H), col1, Y_BIOMES_CSV + 2);
        place(structure.build(font, 0, 0, 130, BOX_H), col1, Y_STRUCTURE + 2);
        place(structureTag.build(font, 0, 0, 130, BOX_H), col2, Y_STRUCTURE + 2);
        place(allowWaterSurface.build(0, 0, 56, BOX_H), col1, Y_WATER_FLOOR + 2);
        place(allowOceanFloor.build(0, 0, 56, BOX_H), col2, Y_WATER_FLOOR + 2);
        place(minYOn.build(0, 0, 48, BOX_H), col1, Y_MINY_MAXY + 2);
        place(minY.build(font, 0, 0, 56, BOX_H), col1 + 56, Y_MINY_MAXY + 2);
        place(maxYOn.build(0, 0, 48, BOX_H), col1 + 56 + 64, Y_MINY_MAXY + 2);
        place(maxY.build(font, 0, 0, 56, BOX_H), col1 + 56 + 64 + 56, Y_MINY_MAXY + 2);
        place(canSeeSky.build(0, 0, 64, BOX_H), col1, Y_CANSEESKY + 2);

        layout();
    }

    private void place(AbstractWidget w, int xOffset, int yLogical) {
        placements.add(new Placement(w, xOffset, yLogical));
        parent.register(w);
    }

    private void layout() {
        int top = scroll.contentTop();
        int viewTop = scroll.viewTop(), viewBottom = scroll.viewBottom();
        for (Placement p : placements) {
            int screenY = top + p.yLogical;
            p.widget.setX(contentX + p.xOffset);
            p.widget.setY(screenY);
            boolean rowFits = screenY >= viewTop && screenY + p.widget.getHeight() <= viewBottom;
            p.widget.visible = rowFits;
            p.widget.active = rowFits;
        }
    }

    private Component reasonLabel(String reason) {
        boolean on = parent != null && parent.draft().spawnReasons.contains(reason);
        return Component.literal((on ? "[x] " : "[ ] ") + reason);
    }

    private void toggleReason(String reason, Button btn) {
        MobOriginDraft d = parent.draft();
        if (!d.spawnReasons.remove(reason)) d.spawnReasons.add(reason);
        btn.setMessage(reasonLabel(reason));
    }

    @Override
    public void pullFromDraft() {
        MobOriginDraft d = parent.draft();
        enabled.setValue(d.spawnRulesEnabled ? "yes" : "no");
        weight.setValue(Double.toString(d.weight));
        timeOfDay.setValue(d.timeOfDay);
        yRangeOn.setValue(d.yRangeEnabled ? "yes" : "no");
        yMin.setValue(Integer.toString(d.yRangeMin));
        yMax.setValue(Integer.toString(d.yRangeMax));
        lightRangeOn.setValue(d.lightRangeEnabled ? "yes" : "no");
        lightMin.setValue(Integer.toString(d.lightRangeMin));
        lightMax.setValue(Integer.toString(d.lightRangeMax));
        mutexGroup.setValue(d.mutexGroup);
        replace.setValue(d.replace ? "yes" : "no");
        for (var e : reasonButtons.entrySet()) e.getValue().setMessage(reasonLabel(e.getKey()));

        dimension.setValue(d.locationDimension);
        biome.setValue(d.locationBiome);
        biomeTag.setValue(d.locationBiomeTag);
        biomesCsv.setValue(String.join(",", d.locationBiomes));
        structure.setValue(d.locationStructure);
        structureTag.setValue(d.locationStructureTag);
        allowWaterSurface.setValue(d.locationAllowWaterSurface ? "yes" : "no");
        allowOceanFloor.setValue(d.locationAllowOceanFloor ? "yes" : "no");
        minYOn.setValue(d.locationMinYEnabled ? "yes" : "no");
        minY.setValue(Integer.toString(d.locationMinY));
        maxYOn.setValue(d.locationMaxYEnabled ? "yes" : "no");
        maxY.setValue(Integer.toString(d.locationMaxY));
        canSeeSky.setValue(d.locationCanSeeSky);
    }

    @Override
    public void pushToDraft() {
        MobOriginDraft d = parent.draft();
        d.spawnRulesEnabled = "yes".equals(enabled.value());
        d.weight = parseDoubleOr(weight.value(), d.weight);
        d.timeOfDay = timeOfDay.value();
        d.yRangeEnabled = "yes".equals(yRangeOn.value());
        d.yRangeMin = parseIntOr(yMin.value(), d.yRangeMin);
        d.yRangeMax = parseIntOr(yMax.value(), d.yRangeMax);
        d.lightRangeEnabled = "yes".equals(lightRangeOn.value());
        d.lightRangeMin = parseIntOr(lightMin.value(), d.lightRangeMin);
        d.lightRangeMax = parseIntOr(lightMax.value(), d.lightRangeMax);
        d.mutexGroup = mutexGroup.value().trim();
        d.replace = "yes".equals(replace.value());

        d.locationDimension = dimension.value().trim();
        d.locationBiome = biome.value().trim();
        d.locationBiomeTag = biomeTag.value().trim();
        d.locationBiomes.clear();
        for (String s : biomesCsv.value().split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) d.locationBiomes.add(t);
        }
        d.locationStructure = structure.value().trim();
        d.locationStructureTag = structureTag.value().trim();
        d.locationAllowWaterSurface = "yes".equals(allowWaterSurface.value());
        d.locationAllowOceanFloor = "yes".equals(allowOceanFloor.value());
        d.locationMinYEnabled = "yes".equals(minYOn.value());
        d.locationMinY = parseIntOr(minY.value(), d.locationMinY);
        d.locationMaxYEnabled = "yes".equals(maxYOn.value());
        d.locationMaxY = parseIntOr(maxY.value(), d.locationMaxY);
        d.locationCanSeeSky = canSeeSky.value();
    }

    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < contentX || mx > contentX + contentW
            || my < scroll.viewTop() || my > scroll.viewBottom()) return false;
        if (!scroll.onScroll(sy)) return false;
        layout();
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        int lx = x + 8;
        int top = scroll.contentTop();

        scroll.beginClip(g);

        CreatorStyle.sectionHeader(g, font, "Spawn rules", lx, top + Y_RULES_HEADER, w - 16);
        g.text(font, "Enabled",     lx, top + Y_ENABLED + 6,     CreatorStyle.LABEL, false);
        g.text(font, "Weight",      lx, top + Y_WEIGHT_TIME + 6, CreatorStyle.LABEL, false);
        g.text(font, "Time of day", x + w / 2 + 6, top + Y_WEIGHT_TIME + 6, CreatorStyle.LABEL, false);
        g.text(font, "Y range",     lx, top + Y_YRANGE + 6,      CreatorStyle.LABEL, false);
        g.text(font, "Light range", lx, top + Y_LIGHTRANGE + 6,  CreatorStyle.LABEL, false);
        CreatorStyle.sectionHeader(g, font, "Spawn reasons (empty = any)",
            lx, top + Y_REASONS_HEADER, w - 16);
        g.text(font, "Mutex group", lx, top + Y_MUTEX + 6,       CreatorStyle.LABEL, false);
        g.text(font, "Replace existing",
            lx, top + Y_REPLACE + 6, CreatorStyle.LABEL, false);

        CreatorStyle.sectionHeader(g, font, "Location filter (optional)",
            lx, top + Y_LOCATION_HEADER, w - 16);
        g.text(font, "Dimension",    lx, top + Y_DIMENSION + 6,    CreatorStyle.LABEL, false);
        g.text(font, "Biome",        lx, top + Y_BIOME_TAG + 6,    CreatorStyle.LABEL, false);
        g.text(font, "Biome tag",    x + w / 2 + 6, top + Y_BIOME_TAG + 6, CreatorStyle.LABEL, false);
        g.text(font, "Biomes (csv)", lx, top + Y_BIOMES_CSV + 6,   CreatorStyle.LABEL, false);
        g.text(font, "Structure",    lx, top + Y_STRUCTURE + 6,    CreatorStyle.LABEL, false);
        g.text(font, "Struct. tag",  x + w / 2 + 6, top + Y_STRUCTURE + 6, CreatorStyle.LABEL, false);
        g.text(font, "Water surf.",  lx, top + Y_WATER_FLOOR + 6,  CreatorStyle.LABEL, false);
        g.text(font, "Ocean floor",  x + w / 2 + 6, top + Y_WATER_FLOOR + 6, CreatorStyle.LABEL, false);
        g.text(font, "Y bounds",     lx, top + Y_MINY_MAXY + 6,    CreatorStyle.LABEL, false);
        g.text(font, "Can see sky",  lx, top + Y_CANSEESKY + 6,    CreatorStyle.LABEL, false);

        scroll.endClip(g);
        scroll.renderScrollbar(g);
    }

    /** A widget + its in-scroll x-offset / logical y. */
    private record Placement(AbstractWidget widget, int xOffset, int yLogical) {}
}
