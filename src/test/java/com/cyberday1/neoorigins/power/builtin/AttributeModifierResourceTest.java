package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apoli lets a modifier take its value from a resource, so a pack can make max health
 * track a counter. A vanilla AttributeModifier holds one fixed number, so the import
 * used to apply the modifier's declared value, which these powers write as 0 because
 * the resource is meant to supply all of it: the power loaded and did nothing.
 */
class AttributeModifierResourceTest {

    private static final String POWER = "smokedemon:lich_health";
    private static final String RESOURCE = "smokedemon:lich_health_resource";

    private static PowerHolder<AttributeModifierPower.Config> holder(String json) {
        var power = new AttributeModifierPower();
        var config = power.codec().parse(JsonOps.INSTANCE,
            JsonParser.parseString(json).getAsJsonObject()).getOrThrow();
        return new PowerHolder<>(Identifier.parse(POWER), power, config,
            Component.empty(), Component.empty());
    }

    private static double maxHealthBonus(ServerPlayer player) {
        var instance = player.getAttribute(Attributes.MAX_HEALTH);
        return instance == null ? 0.0
            : instance.getModifiers().stream()
                .filter(m -> m.id().getPath().startsWith(AttributeModifierPower.MOD_ID_PREFIX))
                .mapToDouble(m -> m.amount()).sum();
    }

    private static void tick(PowerHolder<AttributeModifierPower.Config> power, ServerPlayer sp) {
        // The power only re-reads on a 5-tick boundary.
        sp.tickCount = 5;
        power.onTick(sp);
    }

    @Test
    void theModifierFollowsTheResource() {
        var power = holder("""
            {
              "attribute": "minecraft:generic.max_health",
              "amount": 0,
              "resource": "smokedemon:lich_health_resource",
              "operation": "add_value"
            }
            """);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        power.onGranted(sp);
        assertEquals(0.0, maxHealthBonus(sp), "an unwritten resource reads 0");

        sp.getData(CompatAttachments.resourceState()).set(RESOURCE, 10);
        tick(power, sp);
        assertEquals(10.0, maxHealthBonus(sp), "the modifier did not follow the resource up");

        sp.getData(CompatAttachments.resourceState()).set(RESOURCE, 4);
        tick(power, sp);
        assertEquals(4.0, maxHealthBonus(sp), "the modifier did not follow the resource down");

        power.onRevoked(sp);
        assertEquals(0.0, maxHealthBonus(sp), "the modifier outlived the power");
    }

    @Test
    void aPlainAmountIsUntouchedByTheTick() {
        var power = holder("""
            { "attribute": "minecraft:generic.max_health", "amount": 6, "operation": "add_value" }
            """);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        power.onGranted(sp);
        assertEquals(6.0, maxHealthBonus(sp));
        sp.getData(CompatAttachments.resourceState()).set(RESOURCE, 10);
        tick(power, sp);
        assertEquals(6.0, maxHealthBonus(sp), "a static modifier must ignore every resource");
    }

    /** The reported JSON, as the pack wrote it, through the Origins translator. */
    @Test
    void theImportedApoliShapeCarriesTheResource() {
        Optional<JsonObject> out = OriginsPowerTranslator.translate(Identifier.parse(POWER),
            JsonParser.parseString("""
                {
                  "type": "origins:modify_attribute",
                  "attribute": "minecraft:generic.max_health",
                  "modifier": {
                    "operation": "add_base_early",
                    "resource": "smokedemon:lich_health_resource",
                    "value": 0
                  }
                }
                """).getAsJsonObject());
        assertTrue(out.isPresent(), "the power was dropped");
        JsonObject power = out.orElseThrow();
        assertEquals("neoorigins:attribute_modifier", power.get("type").getAsString());
        assertEquals(RESOURCE, power.get("resource").getAsString(),
            "the resource key was dropped, so the attribute could not follow it");
        assertEquals("add_value", power.get("operation").getAsString(),
            "add_base_early is a flat addition");
    }
}
