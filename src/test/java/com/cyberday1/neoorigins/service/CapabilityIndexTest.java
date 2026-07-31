package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.ModelColorPower;
import com.cyberday1.neoorigins.power.builtin.WallClimbingPower;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the negative filter {@code ActiveOriginService.hasCapability} uses.
 *
 * <p>The filter is only sound while every power type left OUT of the dynamic list
 * has a static {@code capabilities(config)} result that is an upper bound on its
 * player-aware one. A type that quietly gains a player-aware override would make
 * the filter return false for a capability the player really has, silently
 * disabling the power in-game with nothing logged — so the second test asserts the
 * classification stays exhaustive as new types are added.
 */
class CapabilityIndexTest {

    private static <C extends com.cyberday1.neoorigins.api.power.PowerConfiguration>
    PowerHolder<C> holder(String id, com.cyberday1.neoorigins.api.power.PowerType<C> type, C config) {
        return new PowerHolder<>(Identifier.fromNamespaceAndPath("neoorigins", id),
            type, config, Component.empty(), Component.empty());
    }

    @Test
    void staticTypesFeedTheUnionAndDynamicTypesDoNot() {
        PowerHolder<WallClimbingPower.Config> climb =
            holder("climb", new WallClimbingPower(), new WallClimbingPower.Config(""));
        // A conditioned model_color: capabilities(config) returns an EMPTY set while
        // capabilities(player, config) returns the real tag, so it must not be folded
        // into the union or the tag becomes unreachable.
        PowerHolder<ModelColorPower.Config> tint = holder("tint", new ModelColorPower(),
            new ModelColorPower.Config(1.0F, 0.0F, 0.0F, 1.0F, Optional.empty(), ""));

        Set<String> union = new HashSet<>();
        List<PowerHolder<?>> dynamic = new ArrayList<>();
        ActiveOriginService.indexCapabilities(List.of(climb, tint), union, dynamic);

        assertTrue(union.contains("wall_climb"), "plain static type must contribute its tag");
        assertTrue(dynamic.contains(tint), "model_color must be deferred to the live probe");
        assertFalse(dynamic.contains(climb), "plain static type must not be deferred");
        assertTrue(union.stream().noneMatch(t -> t.startsWith("model_color:")),
            "a deferred type must not leak tags into the union");
    }

    @Test
    void everyPlayerAwareOverriderIsClassifiedDynamic() throws Exception {
        List<String> missing = new ArrayList<>();
        int scanned = 0;
        for (Field field : PowerTypes.class.getDeclaredFields()) {
            if (!(field.getGenericType() instanceof ParameterizedType pt)) continue;
            Type[] args = pt.getActualTypeArguments();
            if (args.length != 2 || !(args[1] instanceof Class<?> powerClass)) continue;
            scanned++;
            if (!overridesPlayerAwareCapabilities(powerClass)) continue;
            if (!ActiveOriginService.isDynamicCapabilityType(powerClass)) {
                missing.add(powerClass.getName());
            }
        }
        assertTrue(scanned > 100, "expected to scan the whole PowerTypes registry, saw " + scanned);
        assertTrue(missing.isEmpty(),
            "power types override capabilities(ServerPlayer, Config) but are not classified "
                + "dynamic in ActiveOriginService: " + missing);
    }

    /** True if {@code powerClass} or an ancestor declares its own {@code capabilities(ServerPlayer, C)}. */
    private static boolean overridesPlayerAwareCapabilities(Class<?> powerClass) {
        for (Class<?> c = powerClass; c != null && !c.getName().endsWith(".PowerType"); c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic() || m.isBridge()) continue;
                if (!m.getName().equals("capabilities")) continue;
                if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == ServerPlayer.class) {
                    return true;
                }
            }
        }
        return false;
    }
}
