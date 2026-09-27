package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two origins that mint the same power id no longer overwrite each other's power file. */
class PowerFileCollisionTest {

    private static final Path PACK = RealDatapack.projectPath("build").resolve("test-packs/power-collision");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void clear() throws Exception {
        if (!Files.exists(PACK)) return;
        try (Stream<Path> w = Files.walk(PACK)) {
            for (Path p : w.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    private static OriginDraft.PowerDraft power(String path, String type, String body) {
        OriginDraft.PowerDraft p = new OriginDraft.PowerDraft(
            Identifier.fromNamespaceAndPath(OriginDraft.CUSTOM_NAMESPACE, path), type);
        p.rawJson = body;
        return p;
    }

    private static OriginDraft player(String idPath, OriginDraft.PowerDraft... powers) {
        OriginDraft d = new OriginDraft();
        d.idPath = idPath;
        d.name = idPath;
        for (var p : powers) d.powers.add(p);
        return d;
    }

    private static JsonObject powerFile(String path) throws Exception {
        return JsonParser.parseString(Files.readString(PACK.resolve(
            "data/neoorigins_custom/origins/powers/" + path + ".json"))).getAsJsonObject();
    }

    private static JsonObject file(String rel) throws Exception {
        return JsonParser.parseString(Files.readString(PACK.resolve(rel))).getAsJsonObject();
    }

    @Test
    void aMobOriginDoesNotOverwriteAPlayerOriginsPower() throws Exception {
        assertTrue(CustomPackWriter.write(PACK, player("foo",
            power("foo_persistent_effect", "neoorigins:persistent_effect", "{\"effect\":\"minecraft:speed\"}"))).ok());
        MobOriginDraft mob = new MobOriginDraft();
        mob.idPath = "foo";
        mob.targetEntityType = "minecraft:zombie";
        mob.powers.add(power("foo_persistent_effect", "neoorigins:persistent_effect", "{\"effect\":\"minecraft:slowness\"}"));
        assertTrue(CustomPackWriter.write(PACK, mob).ok());

        assertEquals("minecraft:speed", powerFile("foo_persistent_effect").get("effect").getAsString());
        String mobPower = file("data/neoorigins_custom/origins/mob_origins/foo.json")
            .getAsJsonArray("powers").get(0).getAsString();
        assertNotEquals("neoorigins_custom:foo_persistent_effect", mobPower);
        assertEquals("minecraft:slowness",
            powerFile(Identifier.parse(mobPower).getPath()).get("effect").getAsString());
    }

    @Test
    void aPlayerOriginDoesNotOverwriteAMobOriginsPower() throws Exception {
        MobOriginDraft mob = new MobOriginDraft();
        mob.idPath = "foo";
        mob.targetEntityType = "minecraft:zombie";
        mob.powers.add(power("foo_persistent_effect", "neoorigins:persistent_effect", "{\"effect\":\"minecraft:slowness\"}"));
        assertTrue(CustomPackWriter.write(PACK, mob).ok());
        assertTrue(CustomPackWriter.write(PACK, player("foo",
            power("foo_persistent_effect", "neoorigins:persistent_effect", "{\"effect\":\"minecraft:speed\"}"))).ok());

        assertEquals("minecraft:slowness", powerFile("foo_persistent_effect").get("effect").getAsString());
        String playerPower = file("data/neoorigins_custom/origins/origins/foo.json")
            .getAsJsonArray("powers").get(0).getAsString();
        assertEquals("minecraft:speed",
            powerFile(Identifier.parse(playerPower).getPath()).get("effect").getAsString());
    }

    @Test
    void slashAndUnderscorePathsKeepTheirOwnPowersAndReferencesFollow() throws Exception {
        assertTrue(CustomPackWriter.write(PACK, player("a/b",
            power("a_b_resource", "neoorigins:resource", "{\"max\":10}"))).ok());
        assertTrue(CustomPackWriter.write(PACK, player("a_b",
            power("a_b_resource", "neoorigins:resource", "{\"max\":99}"),
            power("a_b_active_ability", "neoorigins:active_ability",
                "{\"resource_cost\":\"neoorigins_custom:a_b_resource\"}"))).ok());

        assertEquals(10, powerFile("a_b_resource").get("max").getAsInt());
        var ids = file("data/neoorigins_custom/origins/origins/a_b.json").getAsJsonArray("powers");
        String resource = ids.get(0).getAsString();
        assertNotEquals("neoorigins_custom:a_b_resource", resource);
        assertEquals(99, powerFile(Identifier.parse(resource).getPath()).get("max").getAsInt());
        assertEquals(resource, powerFile(Identifier.parse(ids.get(1).getAsString()).getPath())
            .get("resource_cost").getAsString());
    }

    @Test
    void reSavingAnOriginKeepsItsOwnIds() throws Exception {
        for (int i = 0; i < 2; i++) {
            assertTrue(CustomPackWriter.write(PACK, player("foo",
                power("foo_resource", "neoorigins:resource", "{\"max\":" + (10 + i) + "}"))).ok());
        }
        assertEquals("neoorigins_custom:foo_resource",
            file("data/neoorigins_custom/origins/origins/foo.json").getAsJsonArray("powers").get(0).getAsString());
        assertEquals(11, powerFile("foo_resource").get("max").getAsInt());
    }
}
