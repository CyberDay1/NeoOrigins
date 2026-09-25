package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.power.builtin.PersistentEffectPower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped powers still authored with retired {@code neoorigins:} types must keep
 * loading as what the alias table makes of them, whichever pre-parse pass claims
 * them first. Files are found by their authored type, so the counts are measured.
 */
// hub: neoorigins/salvage-vs-alias.md
class ShippedLegacyAliasContentTest {

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.restoreShipped();
    }

    @Test
    void nightVisionLoadsAsAPassiveNightVisionEffect() {
        List<String> ids = shippedOfType("neoorigins:night_vision");
        assertEquals(22, ids.size(), "shipped night_vision count moved: " + ids);
        for (String path : ids) {
            PowerHolder<?> holder = power(path);
            var config = assertInstanceOf(PersistentEffectPower.Config.class, holder.config(), path);
            assertFalse(config.toggleable(), path + " must not claim a skill slot");
            assertEquals(1, config.effects().size(), path);
            assertEquals("minecraft:night_vision",
                BuiltInRegistries.MOB_EFFECT.getKey(config.effects().getFirst().effect().value()).toString(), path);
        }
    }

    @Test
    void foodRestrictionLoadsAsAWhitelistGateOnFoodEaten() {
        List<String> ids = shippedOfType("neoorigins:food_restriction");
        assertEquals(3, ids.size(), "shipped food_restriction count moved: " + ids);
        for (String path : ids) {
            PowerHolder<?> holder = power(path);
            var config = assertInstanceOf(ActionOnEventPower.Config.class, holder.config(), path);
            assertEquals(EventPowerIndex.Event.FOOD_EATEN, config.event(), path);

            // The authored item_tag must reach the gate, negated for a whitelist.
            JsonObject raw = PowerDataManager.INSTANCE.getRawPowerJson(id(path));
            JsonObject gate = raw.getAsJsonObject("entity_action");
            assertEquals("neoorigins:if_else", gate.get("type").getAsString(), path);
            JsonObject not = gate.getAsJsonObject("condition");
            assertEquals("neoorigins:not", not.get("type").getAsString(), path + " is whitelist-mode");
            JsonObject inTag = not.getAsJsonObject("condition");
            assertEquals("#" + authored(path).get("item_tag").getAsString(), inTag.get("tag").getAsString(), path);
            assertEquals("neoorigins:cancel_event",
                gate.getAsJsonObject("if_action").get("type").getAsString(), path);
        }
    }

    private static PowerHolder<?> power(String path) {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id(path));
        assertNotNull(holder, "neoorigins:" + path + " did not load");
        return holder;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }

    private static Path powersDir() {
        return RealDatapack.projectPath("src/main/resources/data/neoorigins/origins/powers");
    }

    private static JsonObject authored(String path) {
        try {
            return JsonParser.parseString(Files.readString(powersDir().resolve(path + ".json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> shippedOfType(String type) {
        try (Stream<Path> files = Files.walk(powersDir())) {
            return files.filter(p -> p.toString().endsWith(".json"))
                .map(p -> powersDir().relativize(p).toString().replace('\\', '/'))
                .map(p -> p.substring(0, p.length() - ".json".length()))
                .filter(p -> {
                    JsonObject j = authored(p);
                    return j.has("type") && type.equals(j.get("type").getAsString());
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
