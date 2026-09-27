package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iron Monk through Load template, the real writer and the real loader: every
 * power it grants at any tier loads, and every power id the clones name is one
 * of those. Palm Strike's stamina cost used to name a power the saved origin no
 * longer granted, so it read 0 and never fired.
 */
class TemplateSaveLoadsTest {

    private static final Path PACK = RealDatapack.projectPath("build").resolve("test-packs/template-save");

    @BeforeAll
    static void boot() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
        clear();
    }

    @AfterAll
    static void restore() throws Exception {
        RealDatapack.restoreShipped();
        clear();
    }

    private static void clear() throws Exception {
        if (!Files.exists(PACK)) return;
        try (Stream<Path> w = Files.walk(PACK)) {
            for (Path p : w.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    @Test
    void ironMonkSavedFromATemplateGrantsWhatItsPowersReference() {
        OriginTemplate t = OriginTemplates.collect().stream()
            .filter(x -> x.originId().getPath().equals("iron_monk")).findFirst().orElseThrow();
        OriginDraft d = new OriginDraft();
        OriginTemplateLoader.load(t, d);
        d = OriginDraftJson.fromJson(OriginDraftJson.toJson(d));
        var result = CustomPackWriter.write(PACK, d);
        assertTrue(result.ok(), result.error());

        RealDatapack.loadWith(PACK);
        Origin saved = OriginDataManager.INSTANCE.getOrigin(Identifier.parse("neoorigins:iron_monk"));
        assertNotNull(saved);
        assertEquals(3, saved.tierPowers().size(), "tiers were dropped");
        Set<Identifier> granted = new java.util.LinkedHashSet<>(saved.powersForTier(3));
        granted.addAll(saved.powers());
        for (Identifier id : granted) {
            assertEquals(OriginDraft.CUSTOM_NAMESPACE, id.getNamespace(), "still grants the shipped " + id);
            JsonObject body = PowerDataManager.INSTANCE.getRawPowerJson(id);
            assertNotNull(body, id + " was not written or does not load");
            for (String key : List.of("resource_cost", "resource", "power")) {
                if (!body.has(key) || !body.get(key).isJsonPrimitive()) continue;
                Identifier ref = Identifier.parse(body.get(key).getAsString());
                assertTrue(granted.contains(ref), id + " " + key + " names " + ref + ", which the origin does not grant");
            }
        }
    }
}
