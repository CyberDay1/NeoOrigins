package com.cyberday1.neoorigins.screen.mobcreator;

import com.cyberday1.neoorigins.screen.creator.CreatorStyle;
import com.cyberday1.neoorigins.screen.creator.widget.CycleSelector;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn-rules tab — edits {@code SpawnRules} on the draft. Phase 4b ships the
 * simple field widgets (enable / weight / time / spawn reasons / mutex /
 * replace / Y / light ranges). Phase 4c adds the Location sub-editor.
 *
 * <p>The {@code spawn_reasons} list is hard-coded rather than reflected off
 * the running enum — its values differ between {@code MobSpawnType} (1.21.1)
 * and {@code EntitySpawnReason} (26.1). The codec rejects names not in the
 * running enum, so the validator catches a bad pick at Save time.
 */
public final class MobSpawnRulesTab implements MobCreatorTab {

    private static final List<String> TIME_OF_DAY = List.of("any", "day", "night");
    private static final List<String> YES_NO = List.of("no", "yes");
    private static final List<String> SPAWN_REASONS = List.of(
        "natural", "spawner", "chunk_generation", "breeding",
        "reinforcement", "event", "spawn_egg", "command",
        "structure", "bucket", "dispenser", "mob_summoned",
        "patrol", "conversion", "jockey", "triggered");

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

    private MobOriginCreatorScreen parent;

    @Override public Component title() {
        return Component.translatable("gui.neoorigins.mob_creator.tab.spawn_rules");
    }
    @Override public Component help() {
        return Component.literal("When (and how often) this origin rolls onto a freshly-spawned mob.");
    }

    @Override
    public void init(MobOriginCreatorScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        reasonButtons.clear();
        Font font = parent.font();
        int labelDx = 8;
        int col1 = x + labelDx + 90;     // field column 1 (left half)
        int col2 = x + w / 2 + 90;       // field column 2 (right half)
        int boxH = 14, rowH = 22;

        parent.register(enabled.build(col1, y + 4, 64, boxH));

        parent.register(weight.build(font, col1, y + rowH + 4, 80, boxH));
        parent.register(timeOfDay.build(col2, y + rowH + 4, 80, boxH));

        parent.register(yRangeOn.build(col1, y + rowH * 2 + 4, 48, boxH));
        parent.register(yMin.build(font, col1 + 56, y + rowH * 2 + 4, 56, boxH));
        parent.register(yMax.build(font, col1 + 56 + 64, y + rowH * 2 + 4, 56, boxH));

        parent.register(lightRangeOn.build(col1, y + rowH * 3 + 4, 48, boxH));
        parent.register(lightMin.build(font, col1 + 56, y + rowH * 3 + 4, 56, boxH));
        parent.register(lightMax.build(font, col1 + 56 + 64, y + rowH * 3 + 4, 56, boxH));

        int gridX = x + labelDx;
        int gridY = y + rowH * 4 + 18;
        int colW = (w - labelDx * 2) / 4;
        for (int i = 0; i < SPAWN_REASONS.size(); i++) {
            int row = i / 4, col = i % 4;
            String reason = SPAWN_REASONS.get(i);
            Button b = Button.builder(reasonLabel(reason),
                btn -> toggleReason(reason, btn))
                .bounds(gridX + col * colW, gridY + row * 18, colW - 4, 16).build();
            reasonButtons.put(reason, b);
            parent.register(b);
        }

        int afterGridY = gridY + 4 * 18 + 6;
        parent.register(mutexGroup.build(font, col1, afterGridY, 140, boxH));
        parent.register(replace.build(col1, afterGridY + rowH, 64, boxH));
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
    }

    private static double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial,
                       int x, int y, int w, int h) {
        Font font = parent.font();
        int lx = x + 8;
        int rowH = 22;
        CreatorStyle.sectionHeader(g, font, "Spawn rules", lx, y, w - 16);

        g.text(font, "Enabled",                lx, y + 6,                CreatorStyle.LABEL, false);
        g.text(font, "Weight",                 lx, y + rowH + 6,         CreatorStyle.LABEL, false);
        g.text(font, "Time of day",            x + w / 2 + 6, y + rowH + 6, CreatorStyle.LABEL, false);
        g.text(font, "Y range",                lx, y + rowH * 2 + 6,     CreatorStyle.LABEL, false);
        g.text(font, "Light range",            lx, y + rowH * 3 + 6,     CreatorStyle.LABEL, false);

        int gridLabelY = y + rowH * 4 + 6;
        CreatorStyle.sectionHeader(g, font, "Spawn reasons (empty = any)", lx, gridLabelY, w - 16);

        int afterGridY = y + rowH * 4 + 18 + 4 * 18 + 6;
        g.text(font, "Mutex group", lx, afterGridY + 4,  CreatorStyle.LABEL, false);
        g.text(font, "Replace existing",
            lx, afterGridY + rowH + 4, CreatorStyle.LABEL, false);

        g.text(font,
            "Weight 0 = never roll. spawn_reasons empty matches any reason.",
            lx, afterGridY + rowH * 2 + 6, CreatorStyle.TEXT_DIM, false);
    }
}
