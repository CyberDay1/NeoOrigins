package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Seal's Grace promises +15% swim speed. Vanilla water travel only reads
 * {@code movement_speed} when the player has water movement efficiency (Depth
 * Strider), so the power must scale NeoForge's {@code swim_speed}, which the water
 * branch of {@code LivingEntity#travel} multiplies in unconditionally.
 */
class SealsGraceSwimSpeedTest {

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RealDatapack.load();
    }

    @Test
    void scalesSwimSpeedByFifteenPercent() {
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(
            ResourceLocation.fromNamespaceAndPath("neoorigins", "class_fisher_swim_speed"));
        assertNotNull(holder, "class_fisher_swim_speed did not load");
        var config = (AttributeModifierPower.Config) holder.config();
        assertEquals(ResourceLocation.parse("neoforge:swim_speed"), config.attribute());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_BASE, config.operation());
        assertEquals(0.15, config.amount(), 1e-9);
    }
}
