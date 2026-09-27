package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.cyberday1.neoorigins.screen.creator.widget.LabeledField;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load template, then Save, of every shipped origin writes back the same origin:
 * every key, the name and description as authored, and every power body, once the
 * clones' new ids are mapped back to the ones they came from.
 */
class TemplateRoundTripCorpusTest {

    private static List<OriginTemplate> templates;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
        templates = OriginTemplates.fromJson(OriginTemplates.toJson(OriginTemplates.collect()));
    }

    /** What the server receives: the draft after the client→server wire round trip. */
    private static OriginDraft loadAndSend(OriginTemplate t) {
        OriginDraft d = new OriginDraft();
        OriginTemplateLoader.load(t, d);
        return OriginDraftJson.fromJson(OriginDraftJson.toJson(d));
    }

    private static Map<String, String> backToSource(OriginTemplate t, OriginDraft d) {
        Map<String, String> back = new HashMap<>();
        List<OriginDraft.PowerDraft> all = new ArrayList<>(d.powers);
        all.addAll(d.tierPowers);
        List<String> src = new ArrayList<>();
        t.powers().keySet().forEach(k -> src.add(k.toString()));
        t.tierPowers().keySet().forEach(k -> src.add(k.toString()));
        assertEquals(src.size(), all.size(), t.originId() + ": power count");
        for (int i = 0; i < all.size(); i++) back.put(all.get(i).powerId.toString(), src.get(i));
        return back;
    }

    @Test
    void everyTemplateSavesBackItsOwnOrigin() {
        assertTrue(templates.size() >= 70, "too few templates: " + templates.size());
        List<String> diffs = new ArrayList<>();
        int withTiers = 0;
        for (OriginTemplate t : templates) {
            JsonObject source = JsonParser.parseString(t.originBody()).getAsJsonObject();
            source.remove("id");
            OriginDraft d = loadAndSend(t);
            if (!d.tierPowers.isEmpty()) withTiers++;
            JsonObject written = CustomPackSerializer.originJson(d);
            JsonElement mapped = PowerIdRemap.rewrite(written, backToSource(t, d));
            JsonObject expected = source.deepCopy();
            if (!expected.has("upgrades")) expected.add("upgrades", new com.google.gson.JsonArray());
            // 26.x: IconCodec#safeStack yields EMPTY while item components are unbound (always
            // in unit tests), so the template carries player_head. Compare against that.
            expected.addProperty("icon", t.icon().toString());
            if (!reparse(expected).equals(reparse(mapped))) {
                diffs.add(t.originId() + "\n   was " + expected + "\n   now " + mapped);
            }
            List<String> refs = new ArrayList<>();
            references(written, refs);
            for (String ref : refs) {
                boolean cloned = t.powers().containsKey(net.minecraft.resources.Identifier.tryParse(ref))
                    && !JsonParser.parseString(t.powers().get(net.minecraft.resources.Identifier.tryParse(ref))).getAsJsonObject().isEmpty()
                    || t.tierPowers().containsKey(net.minecraft.resources.Identifier.tryParse(ref));
                if (cloned) diffs.add(t.originId() + " saved origin still names the uncloned " + ref);
            }
            JsonObject withId = written.deepCopy();
            withId.addProperty("id", d.originId().toString());
            Origin.CODEC.parse(JsonOps.INSTANCE, withId).error()
                .ifPresent(e -> diffs.add(t.originId() + " does not parse: " + e.message()));
        }
        assertTrue(withTiers > 40, "tier powers were not carried: " + withTiers);
        assertEquals(List.of(), diffs, diffs.size() + " of " + templates.size() + " templates changed");
    }

    @Test
    void everyClonedPowerIsItsSourceAndPointsOnlyAtClones() {
        List<String> diffs = new ArrayList<>();
        int stubs = 0;
        for (OriginTemplate t : templates) {
            OriginDraft d = loadAndSend(t);
            Map<String, String> back = backToSource(t, d);
            Map<String, String> sources = new HashMap<>();
            t.powers().forEach((k, v) -> sources.put(k.toString(), v));
            t.tierPowers().forEach((k, v) -> sources.put(k.toString(), v));
            List<OriginDraft.PowerDraft> all = new ArrayList<>(d.powers);
            all.addAll(d.tierPowers);
            for (OriginDraft.PowerDraft p : all) {
                String srcId = back.get(p.powerId.toString());
                JsonObject src = JsonParser.parseString(sources.get(srcId)).getAsJsonObject();
                // collect() stands in {} for a power whose mod is absent (audit A G3); Save refuses those
                if (src.isEmpty()) { stubs++; continue; }
                src.remove("id");
                src.remove("_power_id");
                JsonObject written = CustomPackSerializer.powerJson(p);
                if (!reparse(src).equals(reparse(PowerIdRemap.rewrite(written, back)))) {
                    diffs.add(srcId + "\n   was " + src + "\n   now " + written);
                }
                List<String> refs = new ArrayList<>();
                references(written, refs);
                for (String ref : refs) {
                    if (sources.containsKey(ref)) diffs.add(srcId + " still points at the uncloned " + ref);
                }
            }
        }
        assertTrue(stubs < 60, "stub count moved, re-check G3: " + stubs);
        assertEquals(List.of(), diffs, diffs.size() + " cloned powers differ");
    }

    @Test
    void longDescriptionsAreNotCutByTheIdentityField() {
        String longest = "";
        for (OriginTemplate t : templates) {
            OriginDraft d = loadAndSend(t);
            if (d.description.length() > longest.length()) longest = d.description;
        }
        assertTrue(longest.length() > 256, "no description longer than the old cap: " + longest.length());
        LabeledField f = new LabeledField("description");
        f.build(null, 0, 0, 200, 20);
        f.setValue(longest);
        assertEquals(longest, f.value());
    }

    @Test
    void aWebAuthoredOriginKeepsEveryFieldTheCreatorHasNoRowFor() {
        String body = "{\"name\":\"Wizard\",\"description\":\"Casts.\",\"icon\":\"minecraft:book\","
            + "\"impact\":\"low\",\"order\":3,\"powers\":[\"mypack:mana\",\"mypack:bolt\"],"
            + "\"unchoosable\":true,\"special\":true,\"required_mods\":[\"minecraft\"],"
            + "\"upgrades\":[{\"advancement\":\"minecraft:story/root\",\"origin\":\"mypack:archmage\"}],"
            + "\"spawn_location\":{\"dimension\":\"minecraft:the_nether\"},"
            + "\"figura_model\":\"wizard_hat\",\"figura_models\":[\"a\",\"b\"],"
            + "\"tier_powers\":[{\"tier\":1,\"add\":[\"mypack:big_bolt\"],\"remove\":[\"mypack:bolt\"]}]}";
        var powers = new java.util.LinkedHashMap<net.minecraft.resources.Identifier, String>();
        powers.put(net.minecraft.resources.Identifier.parse("mypack:mana"),
            "{\"type\":\"neoorigins:resource\",\"max\":10}");
        powers.put(net.minecraft.resources.Identifier.parse("mypack:bolt"),
            "{\"type\":\"neoorigins:active_ability\",\"resource_cost\":\"mypack:mana\"}");
        var tier = new java.util.LinkedHashMap<net.minecraft.resources.Identifier, String>();
        tier.put(net.minecraft.resources.Identifier.parse("mypack:big_bolt"),
            "{\"type\":\"neoorigins:active_ability\",\"resource_cost\":\"mypack:mana\"}");
        OriginTemplate t = new OriginTemplate(net.minecraft.resources.Identifier.parse("mypack:wizard"),
            net.minecraft.resources.Identifier.parse("neoorigins:origin"), "Wizard",
            net.minecraft.resources.Identifier.parse("minecraft:book"), 1, body, powers, tier);
        OriginDraft d = loadAndSend(t);
        JsonObject written = CustomPackSerializer.originJson(d);
        JsonObject expected = JsonParser.parseString(body).getAsJsonObject();
        assertEquals(reparse(expected), reparse(PowerIdRemap.rewrite(written, backToSource(t, d))));
        // the clones point at each other, not at the web pack's ids
        String mana = d.powers.get(0).powerId.toString();
        assertTrue(d.powers.get(1).rawJson.contains(mana), d.powers.get(1).rawJson);
        assertTrue(d.tierPowers.get(0).rawJson.contains(mana), d.tierPowers.get(0).rawJson);
    }

    @Test
    void thePowersTabNoLongerRenamesAClonedPower() {
        for (OriginTemplate t : templates) {
            OriginDraft d = new OriginDraft();
            OriginTemplateLoader.load(t, d);
            for (OriginDraft.PowerDraft p : d.powers) {
                assertEquals(p.powerId, d.mintPowerId(p, p.typeId), t.originId() + " renamed " + p.powerId);
            }
        }
    }

    @Test
    void everyTemplateDraftFitsTheSavePacket() {
        int largest = 0;
        String which = "";
        for (OriginTemplate t : templates) {
            OriginDraft d = new OriginDraft();
            OriginTemplateLoader.load(t, d);
            int n = OriginDraftJson.toJson(d).length();
            if (n > largest) { largest = n; which = t.originId().toString(); }
        }
        // SaveCustomOriginPayload reads the draft with FriendlyByteBuf.readUtf's default cap
        assertTrue(largest < 32767, "largest draft " + which + " is " + largest + " chars");
    }

    /** Every string value outside a {@code type} key: the places a power reference can sit. */
    private static void references(JsonElement el, List<String> out) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) out.add(el.getAsString());
        else if (el.isJsonArray()) el.getAsJsonArray().forEach(x -> references(x, out));
        else if (el.isJsonObject()) {
            for (var e : el.getAsJsonObject().entrySet()) if (!"type".equals(e.getKey())) references(e.getValue(), out);
        }
    }

    private static JsonElement reparse(JsonElement el) {
        return JsonParser.parseString(el.toString());
    }
}
