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

        System.out.printf("[custompack-check] %d failures%n", failures);
        if (failures > 0) System.exit(1);
        System.out.println("[custompack-check] OK");
    }

    private static void fail(String msg) {
        System.out.println("[custompack-check] FAIL  " + msg);
    }
}
