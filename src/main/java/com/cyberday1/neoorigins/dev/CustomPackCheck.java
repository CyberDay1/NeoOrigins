package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.service.CustomPackSerializer;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/**
 * Headless guard for the 2.1 creator's datapack <em>shape</em> rules — the
 * silently-breakable invariants {@link CustomPackSerializer} enforces (origin
 * carries no {@code id}/{@code type}; power carries {@code type}; layer patch
 * is additive + deduplicated). Pure: no Minecraft server needed.
 *
 * <p>Invoke via {@code ./gradlew customPackCheck}. Exit 1 on any failure.
 */
public final class CustomPackCheck {

    private CustomPackCheck() {}

    public static void main(String[] args) {
        int failures = 0;

        OriginDraft d = new OriginDraft();
        d.idPath = "test_origin";
        d.name = "Test Origin";
        d.description = "A test.";
        d.impact = 2;
        d.order = 7;
        d.powers.add(new OriginDraft.PowerDraft(
            Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, "test_fly"),
            "neoorigins:flight"));

        // 1. Origin body: no id, no type; expected fields present.
        JsonObject origin = CustomPackSerializer.originJson(d);
        if (origin.has("id"))   { fail("origin JSON must NOT contain 'id'"); failures++; }
        if (origin.has("type")) { fail("origin JSON must NOT contain 'type'"); failures++; }
        for (String k : new String[]{"name", "description", "icon", "impact", "order", "powers", "upgrades"}) {
            if (!origin.has(k)) { fail("origin JSON missing '" + k + "'"); failures++; }
        }
        if (origin.has("impact") && !"medium".equals(origin.get("impact").getAsString())) {
            fail("impact 2 should serialize as \"medium\", got " + origin.get("impact"));
            failures++;
        }
        if (origin.has("powers") && origin.getAsJsonArray("powers").size() != 1) {
            fail("expected 1 power ref in origin"); failures++;
        }

        // 2. Power body: type forced from the draft even if rawJson omits it.
        OriginDraft.PowerDraft p = d.powers.get(0);
        p.rawJson = "{\"some_field\":true}";
        JsonObject power = CustomPackSerializer.powerJson(p);
        if (!"neoorigins:flight".equals(power.get("type").getAsString())) {
            fail("power JSON must carry the draft's type"); failures++;
        }
        if (!power.has("some_field")) { fail("power JSON must preserve raw body fields"); failures++; }

        // 3. Layer patch: additive + deduplicated.
        JsonObject l1 = CustomPackSerializer.layerPatch(null, "neoorigins_custom:a");
        JsonObject l2 = CustomPackSerializer.layerPatch(l1, "neoorigins_custom:a"); // dup
        JsonObject l3 = CustomPackSerializer.layerPatch(l2, "neoorigins_custom:b"); // new
        int n = l3.getAsJsonArray("origins").size();
        if (n != 2) { fail("layer patch should dedup then append (expected 2, got " + n + ")"); failures++; }

        // 4. OriginDraftJson round-trip (the network transport shape).
        d.icon = Identifier.fromNamespaceAndPath("minecraft", "feather");
        String wire = com.cyberday1.neoorigins.service.OriginDraftJson.toJson(d);
        OriginDraft rt = com.cyberday1.neoorigins.service.OriginDraftJson.fromJson(wire);
        if (!rt.idPath.equals(d.idPath) || !rt.name.equals(d.name)
                || rt.impact != d.impact || rt.order != d.order
                || !rt.icon.equals(d.icon) || !rt.layerId.equals(d.layerId)
                || rt.powers.size() != d.powers.size()
                || !rt.powers.get(0).powerId.equals(d.powers.get(0).powerId)
                || !rt.powers.get(0).typeId.equals(d.powers.get(0).typeId)
                || !rt.powers.get(0).rawJson.equals(d.powers.get(0).rawJson)) {
            fail("OriginDraftJson round-trip lost data"); failures++;
        }

        // 5. CustomPackSerializer → OriginDraftReader round-trip (re-open path).
        JsonObject originJson = CustomPackSerializer.originJson(d);
        java.util.Map<String, JsonObject> bodies = new java.util.HashMap<>();
        for (OriginDraft.PowerDraft pp : d.powers) {
            bodies.put(pp.powerId.toString(), CustomPackSerializer.powerJson(pp));
        }
        OriginDraft back = com.cyberday1.neoorigins.service.OriginDraftReader.fromJson(
            d.idPath, d.layerId, originJson, bodies);
        boolean rtOk = back.idPath.equals(d.idPath) && back.name.equals(d.name)
            && back.description.equals(d.description) && back.icon.equals(d.icon)
            && back.impact == d.impact && back.order == d.order
            && back.layerId.equals(d.layerId)
            && back.powers.size() == d.powers.size();
        for (int k = 0; rtOk && k < d.powers.size(); k++) {
            OriginDraft.PowerDraft a = d.powers.get(k), b = back.powers.get(k);
            rtOk = a.powerId.equals(b.powerId) && a.typeId.equals(b.typeId)
                && com.google.gson.JsonParser.parseString(a.rawJson)
                    .equals(com.google.gson.JsonParser.parseString(b.rawJson));
        }
        if (!rtOk) { fail("serializer→OriginDraftReader round-trip lost data"); failures++; }

        // 6. MobOriginDraftJson round-trip including Phase-4 SpawnRules fields.
        com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft md =
            new com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft();
        md.idPath = "test_mob";
        md.name = "Test Mob";
        md.description = "round-trip";
        md.icon = Identifier.fromNamespaceAndPath("minecraft", "rotten_flesh");
        md.targetEntityType = "minecraft:zombie";
        md.powers.add(new OriginDraft.PowerDraft(
            Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, "test_mob_buffs"),
            "neoorigins:persistent_effect"));
        md.spawnRulesEnabled = true;
        md.weight = 0.5;
        md.timeOfDay = "night";
        md.spawnReasons.add("natural");
        md.spawnReasons.add("reinforcement");
        md.mutexGroup = "test_group";
        md.replace = true;
        md.yRangeEnabled = true;
        md.yRangeMin = 60;  md.yRangeMax = 100;
        md.lightRangeEnabled = true;
        md.lightRangeMin = 0;  md.lightRangeMax = 7;
        md.locationDimension = "minecraft:overworld";
        md.locationBiomeTag = "minecraft:is_overworld";
        md.locationBiomes.add("minecraft:plains");
        md.locationAllowWaterSurface = true;
        md.locationMinYEnabled = true; md.locationMinY = 50;
        md.locationCanSeeSky = "true";

        String mwire = com.cyberday1.neoorigins.service.MobOriginDraftJson.toJson(md);
        com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft mrt =
            com.cyberday1.neoorigins.service.MobOriginDraftJson.fromJson(mwire);
        boolean mOk = mrt.idPath.equals(md.idPath) && mrt.name.equals(md.name)
            && mrt.description.equals(md.description) && mrt.icon.equals(md.icon)
            && mrt.targetEntityType.equals(md.targetEntityType)
            && mrt.powers.size() == md.powers.size()
            && mrt.spawnRulesEnabled == md.spawnRulesEnabled
            && Double.compare(mrt.weight, md.weight) == 0
            && mrt.timeOfDay.equals(md.timeOfDay)
            && mrt.spawnReasons.equals(md.spawnReasons)
            && mrt.mutexGroup.equals(md.mutexGroup)
            && mrt.replace == md.replace
            && mrt.yRangeEnabled == md.yRangeEnabled
            && mrt.yRangeMin == md.yRangeMin && mrt.yRangeMax == md.yRangeMax
            && mrt.lightRangeEnabled == md.lightRangeEnabled
            && mrt.lightRangeMin == md.lightRangeMin && mrt.lightRangeMax == md.lightRangeMax
            && mrt.locationDimension.equals(md.locationDimension)
            && mrt.locationBiomeTag.equals(md.locationBiomeTag)
            && mrt.locationBiomes.equals(md.locationBiomes)
            && mrt.locationAllowWaterSurface == md.locationAllowWaterSurface
            && mrt.locationMinYEnabled == md.locationMinYEnabled
            && mrt.locationMinY == md.locationMinY
            && mrt.locationCanSeeSky.equals(md.locationCanSeeSky);
        if (!mOk) { fail("MobOriginDraftJson round-trip lost SpawnRules data"); failures++; }

        // 7. MobCustomPackSerializer emits spawn_rules + location when set,
        //    and omits them when spawnRulesEnabled=false.
        JsonObject mOrigin = com.cyberday1.neoorigins.service.MobCustomPackSerializer.mobOriginJson(md);
        if (!mOrigin.has("spawn_rules")) {
            fail("mob origin JSON missing spawn_rules when enabled"); failures++;
        } else {
            JsonObject sr = mOrigin.getAsJsonObject("spawn_rules");
            if (!"night".equals(sr.get("time_of_day").getAsString())
                || sr.get("weight").getAsDouble() != 0.5
                || !sr.has("location")
                || !"minecraft:overworld".equals(sr.getAsJsonObject("location").get("dimension").getAsString())) {
                fail("spawn_rules JSON missing expected fields"); failures++;
            }
        }
        md.spawnRulesEnabled = false;
        JsonObject mOriginOff = com.cyberday1.neoorigins.service.MobCustomPackSerializer.mobOriginJson(md);
        if (mOriginOff.has("spawn_rules")) {
            fail("mob origin JSON must omit spawn_rules when disabled"); failures++;
        }

        System.out.printf("[custompack-check] %d failures%n", failures);
        if (failures > 0) System.exit(1);
        System.out.println("[custompack-check] OK");
    }

    private static void fail(String msg) {
        System.out.println("[custompack-check] FAIL  " + msg);
    }
}
