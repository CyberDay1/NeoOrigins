package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.compat.condition.TargetCondition;
import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.cyberday1.neoorigins.compat.registry.CompatRegistries;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The mob-side parsers rewrite any verb namespace as the player side does, except an addon's own id. */
class TargetNamespaceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static Sheep sheep() {
        Sheep s = mock(Sheep.class);
        doReturn(EntityType.SHEEP).when(s).getType();
        return s;
    }

    @Test
    void otherApoliNamespacesReachTheMobForm() {
        TargetAction heal = TargetActionParser.parse(json("{\"type\": \"apugli:heal\", \"amount\": 2}"), "test");
        assertNotNull(heal);
        Sheep s = sheep();
        heal.execute(s, mock(ServerPlayer.class));
        verify(s).heal(2.0F);

        TargetCondition type = TargetConditionParser.parse(
            json("{\"type\": \"medievalorigins:entity_type\", \"entity_type\": \"minecraft:sheep\"}"), "test");
        assertNotNull(type);
        assertTrue(type.test(sheep(), mock(ServerPlayer.class)));
        assertNotNull(TargetConditionParser.parseBiEntity(json("""
            {"type": "medievalorigins:target_condition", "condition": {"type": "apugli:living"}}"""), "test"));
    }

    @Test
    void anAddonsOwnIdKeepsItsHandlerAndHasNoMobForm() {
        Identifier action = Identifier.parse("namespacetest:heal");
        Identifier condition = Identifier.parse("namespacetest:living");
        CompatRegistries.registerEntityAction(action, MapCodec.unit(0).codec(), (p, c) -> {});
        CompatRegistries.registerEntityCondition(condition, MapCodec.unit(0).codec(), (p, c) -> true);
        try {
            assertNull(TargetActionParser.parse(json("{\"type\": \"namespacetest:heal\", \"amount\": 2}"), "test"));
            assertNull(TargetConditionParser.parse(json("{\"type\": \"namespacetest:living\"}"), "test"));
            // Unregistered ids in the same namespace still rewrite.
            assertNotNull(TargetActionParser.parse(json("{\"type\": \"namespacetest:extinguish\"}"), "test"));
            assertFalse(TargetConditionParser.parse(json("{\"type\": \"namespacetest:on_fire\"}"), "test")
                .test(sheep(), mock(ServerPlayer.class)));
        } finally {
            CompatRegistries.unregisterAddonAction(action);
            CompatRegistries.unregisterAddonCondition(condition);
        }
    }
}
