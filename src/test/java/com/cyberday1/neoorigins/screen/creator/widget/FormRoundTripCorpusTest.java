package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.service.CustomPackSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped power, opened in the in-game form and saved, comes back as it
 * went in; and an edit inside any row keeps the keys that row never read.
 */
class FormRoundTripCorpusTest {

    private static final String MARK = "__edited_by_test";

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
    }

    private static final class Host implements CreatorHost {
        @Override public <T extends AbstractWidget> T register(T w) { return w; }
        @Override public <T extends AbstractWidget> T registerInputOnly(T w) { return w; }
        @Override public Font font() { return null; }
        @Override public void requestRebuild() { }
        @Override public int hostWidth() { return 480; }
        @Override public int hostHeight() { return 320; }
        @Override public void queueTooltip(List<String> lines, int mx, int my) { }
    }

    private record Opened(JsonObject body, OriginDraft.PowerDraft draft, PowerFormPanel panel) {}

    private static Map<Identifier, Opened> openAll() {
        Map<Identifier, Opened> out = new TreeMap<>();
        for (Identifier id : new TreeMap<>(PowerDataManager.INSTANCE.getPowers()).keySet()) {
            JsonObject raw = PowerDataManager.INSTANCE.getRawPowerJson(id);
            if (raw == null) continue;
            // re-parsed so numbers compare as the text they are on disk
            JsonObject body = JsonParser.parseString(raw.toString()).getAsJsonObject();
            String type = body.has("type") ? body.remove("type").getAsString() : "";
            body.remove("id");
            body.remove("_power_id");
            OriginDraft.PowerDraft pd = new OriginDraft.PowerDraft(id, type);
            pd.rawJson = body.toString();
            PowerFormPanel panel = new PowerFormPanel();
            panel.init(new Host(), pd, false, 0, 0, 480, 320);
            out.put(id, new Opened(body, pd, panel));
        }
        return out;
    }

    @Test
    void savingAnUntouchedShippedPowerChangesNothing() {
        Map<Identifier, Opened> all = openAll();
        assertTrue(all.size() > 800, "corpus too small: " + all.size());
        List<String> changed = new ArrayList<>();
        for (var e : all.entrySet()) {
            Opened o = e.getValue();
            o.panel().push();
            JsonObject written = CustomPackSerializer.powerJson(o.draft());
            written.remove("type");
            JsonObject expected = "neoorigins:starting_equipment".equals(o.draft().typeId)
                ? foldedStartingEquipment(o.body()) : o.body();
            if (!written.equals(expected)) changed.add(e.getKey() + "\n   was " + o.body() + "\n   now " + written);
        }
        assertEquals(List.of(), changed, changed.size() + " of " + all.size() + " shipped powers changed");
    }

    @Test
    void anEditInsideAnyRowKeepsWhatThatRowCouldNotRead() {
        int checked = 0;
        List<String> lost = new ArrayList<>();
        for (var e : openAll().entrySet()) {
            for (JsonElement[] st : e.getValue().panel().rowStates()) {
                JsonElement orig = st[0], before = st[1];
                if (orig == null || before == null || before.isJsonPrimitive()) continue;
                JsonElement merged = FormMerge.merge(orig, before, marked(before));
                checked++;
                if (!orig.equals(unmarked(merged))) lost.add(e.getKey() + "\n   was " + orig + "\n   now " + merged);
            }
        }
        assertTrue(checked > 200, "too few nested rows reached: " + checked);
        assertEquals(List.of(), lost, lost.size() + " rows lost data on edit");
    }

    /**
     * The editor shows starting_equipment only as {@code stacks}, folding a singular
     * item into a one-entry list (PowerFormPanel#collapseStartingEquipment). The
     * runtime reads stacks first, so this is the one change a save may make.
     */
    private static JsonObject foldedStartingEquipment(JsonObject body) {
        JsonObject out = body.deepCopy();
        List<String> singular = List.of("item", "count", "enchantments", "legacy_tag", "components");
        boolean hasStacks = out.has("stacks") && !out.getAsJsonArray("stacks").isEmpty();
        if (!hasStacks && out.has("item") && !out.get("item").getAsString().isBlank()) {
            JsonObject entry = new JsonObject();
            for (String k : singular) if (out.has(k)) entry.add(k, out.get(k));
            JsonArray stacks = new JsonArray();
            stacks.add(entry);
            out.add("stacks", stacks);
        }
        for (String k : singular) out.remove(k);
        return out;
    }

    /** {@code el} with a marker key added to every object in it: an edit at every depth. */
    private static JsonElement marked(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (var en : el.getAsJsonObject().entrySet()) o.add(en.getKey(), marked(en.getValue()));
            o.addProperty(MARK, true);
            return o;
        }
        if (el.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : el.getAsJsonArray()) a.add(marked(x));
            return a;
        }
        return el.deepCopy();
    }

    private static JsonElement unmarked(JsonElement el) {
        JsonElement c = JsonParser.parseString(el.toString());
        strip(c);
        return c;
    }

    private static void strip(JsonElement el) {
        if (el.isJsonObject()) {
            el.getAsJsonObject().remove(MARK);
            for (var en : el.getAsJsonObject().entrySet()) strip(en.getValue());
        } else if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) strip(x);
        }
    }
}
