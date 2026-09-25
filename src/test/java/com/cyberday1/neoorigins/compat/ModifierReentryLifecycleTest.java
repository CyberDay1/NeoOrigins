package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.ModifyLavaSpeedPower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * B1: {@code onLogin} and {@code onRespawn} both re-run {@code onGranted}, so a grant
 * that appends without first removing its own entry applies the modifier once more
 * per death and per relog. Each case asserts the value the consumer produces, not
 * the registry's size, across grant, respawn and relog.
 */
// hub: neoorigins/lifecycle-rig.md
class ModifierReentryLifecycleTest {

    /** 26.x binds item default components on datapack reload; the food stack sets its own. */
    @BeforeAll
    static void bindBread() {
        if (!Items.BREAD.builtInRegistryHolder().areComponentsBound()) {
            Items.BREAD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @AfterEach
    void clearRegistries() {
        NumericModifierRegistry.clearAll();
        ModifyFoodRegistry.clearAll();
        ModifyCraftingRegistry.clearAll();
    }

    @Test
    void nativeLavaSpeedHoldsAcrossRespawnAndRelog() {
        var holder = new PowerHolder<>(id("native_lava"), new ModifyLavaSpeedPower(),
            new ModifyLavaSpeedPower.Config("multiply_base", 0.5, ""), Component.empty(), Component.empty());
        assertStable("neoorigins:modify_lava_speed", holder, PlayerLifecycle.player(),
            sp -> NumericModifierRegistry.applyByUuid(sp.getUUID(), NumericModifierRegistry.Kind.LAVA_SPEED, 1.0));
    }

    @Test
    void compatLavaSpeedHoldsAcrossRespawnAndRelog() {
        var holder = compat("compat_lava", "origins:modify_lava_speed",
            "{\"modifier\":{\"operation\":\"multiply_base\",\"value\":0.5}}");
        assertStable("apoli:modify_lava_speed", holder, PlayerLifecycle.player(),
            sp -> NumericModifierRegistry.applyByUuid(sp.getUUID(), NumericModifierRegistry.Kind.LAVA_SPEED, 1.0));
    }

    @Test
    void compatXpGainHoldsAcrossRespawnAndRelog() {
        var holder = compat("compat_xp", "origins:modify_xp_gain",
            "{\"modifier\":{\"operation\":\"multiply_base\",\"value\":0.5}}");
        assertStable("apoli:modify_xp_gain", holder, PlayerLifecycle.player(),
            sp -> NumericModifierRegistry.apply(sp, NumericModifierRegistry.Kind.XP_GAIN, 10.0));
    }

    /** The registry alone proves nothing: XP gain is applied only by the XpChange handler. */
    @Test
    void compatXpGainReachesTheXpEvent() {
        var holder = compat("compat_xp_event", "origins:modify_xp_gain",
            "{\"modifier\":{\"operation\":\"multiply_base\",\"value\":0.5}}");
        ServerPlayer sp = PlayerLifecycle.player();
        PlayerLifecycle.grant(holder, sp);
        var event = new PlayerXpEvent.XpChange(sp, 10);
        CompatEventPowers.onXpChange(event);
        assertEquals(15, event.getAmount(), "modify_xp_gain did not reach the XP a player gains");
    }

    /** Runs the real {@code onFoodEaten} consumer, which chains entries, so duplicates compound. */
    @Test
    void compatModifyFoodHoldsAcrossRespawnAndRelog() {
        var holder = compat("compat_food", "origins:modify_food",
            "{\"food_modifier\":{\"operation\":\"multiply_base\",\"value\":0.5}}");
        assertStable("apoli:modify_food", holder, PlayerLifecycle.realPlayer(),
            ModifierReentryLifecycleTest::nutritionFromFourPointFood);
    }

    /** The consumer stops at the first match, so this pins the entry count: the fix is consistency. */
    @Test
    void compatModifyCraftingHoldsOneEntry() {
        var holder = compat("compat_craft", "origins:modify_crafting",
            "{\"recipe\":\"minecraft:bread\",\"result\":{\"item\":\"minecraft:cake\"}}");
        assertStable("apoli:modify_crafting", holder, PlayerLifecycle.player(),
            sp -> ModifyCraftingRegistry.getEntries(sp).size());
    }

    private static void assertStable(String route, PowerHolder<?> holder, ServerPlayer sp,
                                     ToDoubleFunction<ServerPlayer> observe) {
        PlayerLifecycle.grant(holder, sp);
        double granted = observe.applyAsDouble(sp);
        PlayerLifecycle.respawn(holder, sp);
        double afterRespawn = observe.applyAsDouble(sp);
        PlayerLifecycle.relog(holder, sp);
        double afterRelog = observe.applyAsDouble(sp);
        assertEquals(granted, afterRespawn, 1e-9, route + " drifted on respawn");
        assertEquals(granted, afterRelog, 1e-9, route + " drifted on relog");

        PlayerLifecycle.revoke(holder, sp);
        PlayerLifecycle.grant(holder, sp);
        assertEquals(granted, observe.applyAsDouble(sp), 1e-9, route + " drifted on revoke + re-grant");
    }

    private static double nutritionFromFourPointFood(ServerPlayer sp) {
        ItemStack stack = new ItemStack(Items.BREAD);
        stack.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(4).saturationModifier(0f).build());
        sp.getFoodData().setFoodLevel(0);
        sp.getFoodData().setSaturation(0f);
        CompatEventPowers.onFoodEaten(new LivingEntityUseItemEvent.Finish(sp, stack, 0, ItemStack.EMPTY));
        // Vanilla's own +4 never ran, so the food level is exactly the modifier's delta.
        return sp.getFoodData().getFoodLevel();
    }

    private static PowerHolder<CompatPower.Config> compat(String path, String type, String body) {
        Identifier id = id(path);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        json.addProperty("type", type);
        CompatPower.Config cfg = OriginsCompatPowerLoader.compileForTest(id, type, json);
        assertNotNull(cfg, type + " did not compile");
        return new PowerHolder<>(id, CompatPower.INSTANCE, cfg, Component.empty(), Component.empty());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins_test", path);
    }
}
