package com.cyberday1.neoorigins.power.schemaform;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G1: the {@code *|operation} hint replaced the declared options on
 * {@code modify_lava_speed} and {@code modify_flight_speed}, so the creator offered
 * {@code add_multiplied_base}, which {@code OriginsModifierMath} reads as an addition.
 * A declared option list is the parser's vocabulary and must reach the creator unchanged.
 */
class FormModelDeclaredOptionsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void speedModifiersOfferTheOperationsTheMathReads() {
        for (String type : List.of("neoorigins:modify_lava_speed", "neoorigins:modify_flight_speed")) {
            assertEquals(List.of("addition", "multiply_base", "multiply_total"),
                creatorOptions(type, "operation"), type);
        }
    }

    @Test
    void everyDeclaredOptionListReachesTheCreatorUnchanged() {
        List<String> drift = new ArrayList<>();
        int checked = 0;
        for (String type : BuiltinPowers.ids()) {
            for (FieldSpec f : BuiltinPowers.fieldsFor(type)) {
                if (f.enumValues().isEmpty()) continue;
                checked++;
                List<String> offered = creatorOptions(type, f.name());
                if (!f.enumValues().equals(offered)) drift.add(type + "." + f.name() + " declares " + f.enumValues() + ", creator offers " + offered);
            }
        }
        assertTrue(checked > 0, "no declared option lists found - the check is vacuous");
        assertTrue(drift.isEmpty(), String.join("\n", drift));
    }

    private static List<String> creatorOptions(String type, String field) {
        for (FormFieldSpec s : FormModel.forPower(ResourceLocation.parse(type))) {
            if (s.name().equals(field)) return s.enumValues() == null ? List.of() : s.enumValues();
        }
        return List.of();
    }
}
