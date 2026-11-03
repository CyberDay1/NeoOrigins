package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The public condition surface: parseCondition and conditionCodec. */
class ApiConditionsTest {

    private static final String SUBMERGED = "{\"type\": \"neoorigins:submerged_in\", \"fluid\": \"minecraft:water\"}";

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    record AddonConfig(Predicate<ServerPlayer> condition, int amount) {
        static final Codec<AddonConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            NeoOriginsAPI.conditionCodec().optionalFieldOf("condition", p -> true).forGetter(AddonConfig::condition),
            Codec.INT.fieldOf("amount").forGetter(AddonConfig::amount)
        ).apply(i, AddonConfig::new));
    }

    @Test
    void parsesAConditionAsAPlayerPredicate() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        assertFalse(NeoOriginsAPI.parseCondition(json(SUBMERGED), "test").test(sp), "a dry player isn't submerged");
        JsonObject inverted = json(SUBMERGED);
        inverted.addProperty("inverted", true);
        assertTrue(NeoOriginsAPI.parseCondition(inverted, "test").test(sp));
    }

    @Test
    void anUnknownConditionFailsClosed() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        assertFalse(NeoOriginsAPI.parseCondition(json("{\"type\": \"testmod:nope\"}"), "test").test(sp));
    }

    @Test
    void anAddonCodecDecodesAConditionFieldOrAnArrayOfThem() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        AddonConfig absent = AddonConfig.CODEC.parse(JsonOps.INSTANCE, json("{\"amount\": 2}")).getOrThrow();
        assertTrue(absent.condition().test(sp));

        AddonConfig single = AddonConfig.CODEC.parse(JsonOps.INSTANCE,
            json("{\"amount\": 2, \"condition\": " + SUBMERGED + "}")).getOrThrow();
        assertFalse(single.condition().test(sp));

        AddonConfig all = AddonConfig.CODEC.parse(JsonOps.INSTANCE, json("{\"amount\": 2, \"condition\": ["
            + "{\"type\": \"neoorigins:constant\", \"value\": true}, " + SUBMERGED + "]}")).getOrThrow();
        assertFalse(all.condition().test(sp), "every condition in an array must pass");
    }

    @Test
    void aDecodedConditionEncodesBackToItsJson() {
        JsonElement source = json(SUBMERGED);
        Predicate<ServerPlayer> parsed = NeoOriginsAPI.conditionCodec().parse(JsonOps.INSTANCE, source).getOrThrow();
        assertEquals(source, NeoOriginsAPI.conditionCodec().encodeStart(JsonOps.INSTANCE, parsed).getOrThrow());
        assertTrue(NeoOriginsAPI.conditionCodec().encodeStart(JsonOps.INSTANCE, p -> true).isError());
    }

    @Test
    void aNonObjectIsADecodeError() {
        assertTrue(NeoOriginsAPI.conditionCodec().parse(JsonOps.INSTANCE, JsonParser.parseString("\"x\"")).isError());
    }
}
