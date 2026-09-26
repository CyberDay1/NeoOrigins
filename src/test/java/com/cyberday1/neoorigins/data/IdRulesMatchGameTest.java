package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The web exporter's id cases say what the game accepts; this keeps them honest. */
class IdRulesMatchGameTest {

    @Test
    void everySharedCaseMatchesResourceLocation() throws Exception {
        var cases = JsonParser.parseString(Files.readString(RealDatapack.projectPath(
            "website/src/lib/datapack/__tests__/id-cases.json"))).getAsJsonObject().getAsJsonArray("cases");
        assertTrue(cases.size() > 10);
        for (JsonElement c : cases) {
            String id = c.getAsJsonArray().get(0).getAsString();
            boolean valid = c.getAsJsonArray().get(1).getAsBoolean();
            assertEquals(valid, ResourceLocation.tryParse(id) != null, "\"" + id + "\"");
        }
    }
}
