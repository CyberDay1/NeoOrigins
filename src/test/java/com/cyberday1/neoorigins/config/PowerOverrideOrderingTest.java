package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ConditionPassivePower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A {@code power_overrides.toml} multiplier on a {@code damage_in_water} power must
 * change the damage dealt. The alias remap consumes {@code multiplier}, so the
 * override has to land before it; applied after, it is a dead field.
 */
// hub: neoorigins/salvage-vs-alias.md
class PowerOverrideOrderingTest {

    private static final String POWER = "neoorigins:vampire_water_weakness";
    private static final Map<String, ModConfigSpec.ConfigValue<?>> FIELDS =
        PowerOverridesConfig.POWER_OVERRIDES.get(POWER);
    private static final ModConfigSpec.ConfigValue<?> SHIPPED = FIELDS.get("multiplier");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restore() {
        FIELDS.put("multiplier", SHIPPED);
        RealDatapack.restoreShipped();
    }

    @Test
    void multiplierOverrideScalesTheWaterDamage() {
        assertEquals(0.5f, damageWith(0.5), 1e-6,
            "the multiplier override (0.5 x damage_per_second 1.0) did not reach the damage action");
    }

    @Test
    void multiplierOverrideOfZeroStopsTheWaterDamage() {
        assertEquals(0f, damageWith(0.0), 0f,
            "the multiplier override of 0 did not disable the water damage");
    }

    /** Loads the shipped data with the override set, fires one interval, returns the damage (0 if none). */
    private static float damageWith(double multiplier) {
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Object> cv = Mockito.mock(ModConfigSpec.ConfigValue.class);
        Mockito.when(cv.get()).thenReturn(multiplier);
        Mockito.when(cv.getDefault()).thenReturn(1.0);
        FIELDS.put("multiplier", cv);
        RealDatapack.restoreShipped();

        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(Identifier.parse(POWER));
        assertNotNull(holder, POWER + " did not load");
        var config = (ConditionPassivePower.Config) holder.config();

        ServerPlayer sp = Mockito.mock(ServerPlayer.class, Mockito.RETURNS_DEEP_STUBS);
        config.action().execute(sp);
        ArgumentCaptor<Float> amount = ArgumentCaptor.forClass(Float.class);
        Mockito.verify(sp, Mockito.atMost(1)).hurt(Mockito.<DamageSource>any(), amount.capture());
        return amount.getAllValues().isEmpty() ? 0f : amount.getValue();
    }
}
