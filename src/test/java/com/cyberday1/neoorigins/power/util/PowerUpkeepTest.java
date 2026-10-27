package com.cyberday1.neoorigins.power.util;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.power.builtin.ConditionPassivePower;
import com.cyberday1.neoorigins.power.builtin.EffectOverTimePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toggle and effect_over_time upkeep: charged on its interval, all-or-nothing,
 * and a toggle that can't pay switches itself off.
 */
class PowerUpkeepTest {

    private static final String MANA = "neoorigins:test_upkeep_mana";
    private static final ResourceLocation TOGGLE_ID = ResourceLocation.parse("neoorigins:test_upkeep_toggle");

    private static boolean loadedToggles;
    private Consumer<ServerPlayer> previousResync;
    private int resyncs;

    @BeforeAll
    static void loadConfig() {
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
    }

    @AfterAll
    static void unloadConfig() {
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @BeforeEach
    void stubResync() {
        resyncs = 0;
        previousResync = PowerUpkeep.setResyncForTest(p -> resyncs++);
    }

    @AfterEach
    void restore() {
        PowerUpkeep.setResyncForTest(previousResync);
        PowerUpkeep.register(TOGGLE_ID, null);
    }

    // ── A minimal toggle that counts its own effect calls ───────────────────

    record TestConfig() implements PowerConfiguration {}

    static final class CountingToggle extends AbstractTogglePower<TestConfig> {
        int ticks;
        int removals;
        @Override public Codec<TestConfig> codec() { return Codec.unit(new TestConfig()); }
        @Override protected void tickEffect(ServerPlayer player, TestConfig config) { ticks++; }
        @Override protected void removeEffect(ServerPlayer player, TestConfig config) { removals++; }
    }

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        sp.getFoodData().setFoodLevel(20);
        sp.getFoodData().setSaturation(0f);
        return sp;
    }

    private static void setMana(ServerPlayer sp, int value) {
        sp.getData(CompatAttachments.resourceState()).set(MANA, value);
    }

    private static int mana(ServerPlayer sp) {
        return sp.getData(CompatAttachments.resourceState()).get(MANA, 0);
    }

    private static PowerUpkeep.Upkeep upkeep(int hunger, int mana, int interval, EntityCondition cond) {
        return new PowerUpkeep.Upkeep(hunger, mana > 0 ? MANA : "", mana, interval, cond);
    }

    private static <C extends PowerConfiguration> void tickTo(ServerPlayer sp, PowerHolder<C> holder, int from, int to) {
        for (int t = from; t <= to; t++) {
            sp.tickCount = t;
            holder.onTick(sp);
        }
    }

    private static boolean off(ServerPlayer sp, ResourceLocation id) {
        return sp.getData(OriginAttachments.originData()).isPowerToggledOff(id.toString());
    }

    // ── Toggles ─────────────────────────────────────────────────────────────

    @Test
    void aToggleOnPaysEveryInterval() {
        ServerPlayer sp = player();
        setMana(sp, 12);
        CountingToggle type = new CountingToggle();
        PowerUpkeep.register(TOGGLE_ID, upkeep(0, 5, 20, EntityCondition.alwaysTrue()));
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 40);

        assertEquals(2, mana(sp), "charged on ticks 20 and 40 only");
        assertEquals(40, type.ticks, "the effect runs every tick it is paid up");
        assertFalse(off(sp, TOGGLE_ID));
    }

    @Test
    void aToggleThatCannotPaySwitchesOffAndKeepsWhatItHad() {
        ServerPlayer sp = player();
        setMana(sp, 3);
        CountingToggle type = new CountingToggle();
        PowerUpkeep.register(TOGGLE_ID, upkeep(0, 5, 20, EntityCondition.alwaysTrue()));
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 30);

        assertTrue(off(sp, TOGGLE_ID), "switched off at the first charge it could not pay");
        assertEquals(19, type.ticks, "ran on ticks 1-19, then never again");
        assertEquals(1, type.removals, "torn down exactly as a keypress would");
        assertEquals(3, mana(sp), "nothing is deducted from a short bar");
        assertEquals(1, resyncs, "the client is told the toggle went off");
    }

    @Test
    void aCostItCannotFullyPayDeductsNothing() {
        ServerPlayer sp = player();
        setMana(sp, 3);
        CountingToggle type = new CountingToggle();
        PowerUpkeep.register(TOGGLE_ID, upkeep(4, 5, 20, EntityCondition.alwaysTrue()));
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 20, 20);

        assertTrue(off(sp, TOGGLE_ID));
        assertEquals(20, sp.getFoodData().getFoodLevel(), "the affordable hunger half is not taken either");
        assertEquals(3, mana(sp));
    }

    @Test
    void aFalseUpkeepConditionChargesNothing() {
        ServerPlayer sp = player();
        setMana(sp, 0);
        CountingToggle type = new CountingToggle();
        PowerUpkeep.register(TOGGLE_ID, upkeep(0, 5, 20, EntityCondition.alwaysFalse()));
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 60);

        assertFalse(off(sp, TOGGLE_ID), "nothing was owed, so an empty bar does not matter");
        assertEquals(60, type.ticks);
    }

    @Test
    void aToggleWithNoCostBehavesAsBefore() {
        ServerPlayer sp = player();
        setMana(sp, 0);
        CountingToggle type = new CountingToggle();
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 100);

        assertFalse(off(sp, TOGGLE_ID));
        assertEquals(100, type.ticks);
        assertEquals(0, type.removals);
        assertEquals(0, resyncs);
        assertFalse(type.hasUpkeep(new TestConfig(), TOGGLE_ID), "a keyless one is still released at login");
    }

    @Test
    void aToggleThatIsOffIsNotCharged() {
        ServerPlayer sp = player();
        setMana(sp, 10);
        CountingToggle type = new CountingToggle();
        PowerUpkeep.register(TOGGLE_ID, upkeep(0, 5, 20, EntityCondition.alwaysTrue()));
        sp.getData(OriginAttachments.originData()).setPowerToggledOff(TOGGLE_ID.toString(), true);
        PowerHolder<TestConfig> holder = new PowerHolder<>(TOGGLE_ID, type, new TestConfig(),
            Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 40);

        assertEquals(10, mana(sp));
        assertTrue(type.hasUpkeep(new TestConfig(), TOGGLE_ID), "so login leaves it off rather than forcing it on");
    }

    // ── Charges ─────────────────────────────────────────────────────────────

    @Test
    void aResourceCostFallsBackToHungerWhenBarsAreDisabled() {
        ServerPlayer sp = player();
        setMana(sp, 10);
        PowerUpkeep.Charge charge = PowerUpkeep.Charge.of(1, MANA, 4, true);

        assertEquals(new PowerUpkeep.Charge(5, "", 0), charge);
        assertTrue(charge.pay(sp));
        assertEquals(15, sp.getFoodData().getFoodLevel());
        assertEquals(10, mana(sp), "the bar is not touched while bars are disabled");
    }

    @Test
    void theFallbackAppliesToTheTickPathToo() {
        ServerPlayer sp = player();
        setMana(sp, 0);
        PowerUpkeep.Upkeep u = upkeep(0, 3, 20, EntityCondition.alwaysTrue());
        sp.tickCount = 20;

        assertEquals(PowerUpkeep.Outcome.SHORT, PowerUpkeep.chargeIfDue(sp, u, false));
        assertEquals(PowerUpkeep.Outcome.PAID, PowerUpkeep.chargeIfDue(sp, u, true));
        assertEquals(17, sp.getFoodData().getFoodLevel());
    }

    @Test
    void parseReadsEveryFieldAndClampsTheInterval() {
        JsonObject json = JsonParser.parseString("""
            {"hunger_cost": 2, "resource_cost": "%s", "resource_cost_amount": 7,
             "upkeep_interval": 0, "upkeep_condition": {"type": "neoorigins:creative_flying"}}
            """.formatted(MANA)).getAsJsonObject();
        PowerUpkeep.Upkeep u = PowerUpkeep.parse(json, "neoorigins:test");

        assertEquals(2, u.hungerCost());
        assertEquals(MANA, u.resourceCost());
        assertEquals(7, u.resourceCostAmount());
        assertEquals(1, u.interval());
        assertFalse(u.isFree());
        assertTrue(PowerUpkeep.parse(new JsonObject(), "neoorigins:test").isFree(), "every field is optional");
    }

    @Test
    void theLoaderRecordsUpkeepForSharedFieldTogglesOnly() {
        JsonObject json = JsonParser.parseString("{\"hunger_cost\": 1}").getAsJsonObject();
        ResourceLocation aura = ResourceLocation.parse("neoorigins:test_upkeep_loader_aura");
        try {
            com.cyberday1.neoorigins.data.PowerDataManager.registerUpkeep(
                TOGGLE_ID, new com.cyberday1.neoorigins.power.builtin.CreativeFlightPower(), json);
            com.cyberday1.neoorigins.data.PowerDataManager.registerUpkeep(aura, new EffectOverTimePower(), json);

            assertEquals(1, PowerUpkeep.forPower(TOGGLE_ID).hungerCost());
            assertTrue(PowerUpkeep.forPower(aura).isFree(), "effect_over_time reads its own fields");
        } finally {
            PowerUpkeep.register(aura, null);
        }
    }

    // ── Powers with no toggle ───────────────────────────────────────────────

    @Test
    void aPlainPassiveEffectOverTimeSkipsAPulseItCannotPay() {
        ServerPlayer sp = player();
        setMana(sp, 7);
        AtomicInteger pulses = new AtomicInteger();
        AtomicInteger elses = new AtomicInteger();
        EffectOverTimePower type = new EffectOverTimePower();
        EffectOverTimePower.Config config = new EffectOverTimePower.Config(
            EffectOverTimePower.PASSIVE, false, 20, EntityCondition.alwaysTrue(),
            p -> pulses.incrementAndGet(), p -> elses.incrementAndGet(),
            0, MANA, 5, EntityCondition.alwaysTrue(), false, "neoorigins:effect_over_time", "", false);
        PowerHolder<EffectOverTimePower.Config> holder = new PowerHolder<>(
            ResourceLocation.parse("neoorigins:test_upkeep_aura"), type, config, Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 40);

        assertEquals(1, pulses.get(), "paid for tick 20, skipped tick 40");
        assertEquals(0, elses.get(), "a skipped pulse runs nothing at all");
        assertEquals(2, mana(sp));

        setMana(sp, 5);
        tickTo(sp, holder, 41, 60);
        assertEquals(2, pulses.get(), "it pulses again as soon as it can pay");
    }

    @Test
    void aToggleablePassiveEffectOverTimeSwitchesOff() {
        ServerPlayer sp = player();
        setMana(sp, 4);
        AtomicInteger pulses = new AtomicInteger();
        ResourceLocation id = ResourceLocation.parse("neoorigins:test_upkeep_toggle_aura");
        EffectOverTimePower type = new EffectOverTimePower();
        EffectOverTimePower.Config config = new EffectOverTimePower.Config(
            EffectOverTimePower.PASSIVE, true, 20, EntityCondition.alwaysTrue(),
            p -> pulses.incrementAndGet(), EntityAction.noop(),
            0, MANA, 5, EntityCondition.alwaysTrue(), false, "neoorigins:effect_over_time", "", false);
        PowerHolder<EffectOverTimePower.Config> holder = new PowerHolder<>(
            id, type, config, Component.empty(), Component.empty());

        tickTo(sp, holder, 1, 20);

        assertTrue(off(sp, id));
        assertEquals(0, pulses.get());
        assertEquals(4, mana(sp));
        assertTrue(type.hasUpkeep(config, id));
    }

    @Test
    void aConditionPassiveWithNoToggleLapsesUntilItCanPay() {
        ServerPlayer sp = player();
        setMana(sp, 0);
        AtomicInteger pulses = new AtomicInteger();
        ResourceLocation id = ResourceLocation.parse("neoorigins:test_upkeep_passive");
        ConditionPassivePower type = new ConditionPassivePower();
        ConditionPassivePower.Config config = new ConditionPassivePower.Config(
            10, EntityCondition.alwaysTrue(), p -> pulses.incrementAndGet(), EntityAction.noop(),
            false, false, true, "neoorigins:condition_passive", "", false);
        PowerUpkeep.register(id, upkeep(0, 5, 20, EntityCondition.alwaysTrue()));
        PowerHolder<ConditionPassivePower.Config> holder = new PowerHolder<>(
            id, type, config, Component.empty(), Component.empty());
        try {
            tickTo(sp, holder, 1, 39);
            assertEquals(1, pulses.get(), "pulsed at 10, lapsed at 20, so 30 is skipped");
            assertTrue(PowerUpkeep.isLapsed(sp, id));
            assertFalse(off(sp, id), "there is no toggle to switch off");

            setMana(sp, 5);
            tickTo(sp, holder, 40, 50);
            assertFalse(PowerUpkeep.isLapsed(sp, id), "paid at 40");
            assertEquals(3, pulses.get(), "pulsing again at 40 and 50");
        } finally {
            PowerUpkeep.register(id, null);
            PowerUpkeep.clearPlayer(sp.getUUID());
        }
    }
}
