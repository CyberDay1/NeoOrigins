package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.compat.modifier.FloatModifier;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.cyberday1.neoorigins.service.EventPowerIndex.Event;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code action_on_event} registers closures with {@link EventPowerIndex} at grant
 * time, so its {@code power_condition} has to be read when an event is dispatched.
 * It used to be ignored on both the action and the modifier path.
 *
 * <p>Each test grants through the real {@link PowerHolder} on a genuine
 * {@link ServerPlayer} and flips the gate between dispatches.
 */
class ActionOnEventPowerConditionTest {

    private static final ActionOnEventPower TYPE = new ActionOnEventPower();

    private static ActionOnEventPower.Config config(String powerId, Event event, EntityCondition condition,
                                                    EntityAction action, FloatModifier modifier, int cooldown) {
        return new ActionOnEventPower.Config(event, condition, action, modifier, Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            0, cooldown, powerId, "neoorigins:action_on_event");
    }

    private static PowerHolder<ActionOnEventPower.Config> holder(ActionOnEventPower.Config config,
                                                                 EntityCondition gate,
                                                                 PowerHolder.ConditionMode mode) {
        return new PowerHolder<>(Identifier.parse(config.powerId()), TYPE, config,
            Component.empty(), Component.empty(), false, gate, mode);
    }

    private static PowerHolder<ActionOnEventPower.Config> allow(ActionOnEventPower.Config config, AtomicBoolean open) {
        return holder(config, p -> open.get(), PowerHolder.ConditionMode.ALLOW);
    }

    private static ActionOnEventPower.Config counting(String id, AtomicInteger fired, int cooldown) {
        return config(id, Event.BLOCK_BREAK, EntityCondition.alwaysTrue(),
            p -> fired.incrementAndGet(), FloatModifier.identity(), cooldown);
    }

    @Test
    void actionWaitsForAnOpenGate() {
        AtomicBoolean open = new AtomicBoolean(false);
        AtomicInteger fired = new AtomicInteger();
        var h = allow(counting("test:aoe_action", fired, 0), open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);

        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(0, fired.get(), "the action ran with its power_condition shut");

        open.set(true);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(1, fired.get(), "the action did not run once the gate opened");
        h.onRevoked(sp);
    }

    @Test
    void modifierIsIdentityWhileShut() {
        AtomicBoolean open = new AtomicBoolean(false);
        var h = allow(config("test:aoe_mod", Event.MOD_EXHAUSTION, EntityCondition.alwaysTrue(),
            EntityAction.noop(), base -> base * 2f, 0), open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);

        assertEquals(1f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f,
            "the modifier applied with its power_condition shut");
        open.set(true);
        assertEquals(2f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f);
        h.onRevoked(sp);
    }

    @Test
    void denyModeBlocksWhileTheConditionHolds() {
        AtomicBoolean holds = new AtomicBoolean(true);
        AtomicInteger fired = new AtomicInteger();
        var cfg = config("test:aoe_deny", Event.BLOCK_BREAK, EntityCondition.alwaysTrue(),
            p -> fired.incrementAndGet(), base -> base + 1f, 0);
        var h = holder(cfg, p -> holds.get(), PowerHolder.ConditionMode.DENY);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);

        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(0, fired.get());
        assertEquals(1f, EventPowerIndex.dispatchModifier(sp, Event.BLOCK_BREAK, null, 1f), 1e-6f);

        holds.set(false);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(1, fired.get());
        assertEquals(2f, EventPowerIndex.dispatchModifier(sp, Event.BLOCK_BREAK, null, 1f), 1e-6f);
        h.onRevoked(sp);
    }

    @Test
    void noPowerConditionBehavesAsBefore() {
        AtomicInteger fired = new AtomicInteger();
        var h = holder(counting("test:aoe_plain", fired, 0), null, PowerHolder.ConditionMode.DENY);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(2, fired.get());
        h.onRevoked(sp);
    }

    @Test
    void ownConditionStillGatesWithTheGateOpen() {
        // The codec path, so `condition` is read as this type's own field.
        var cfg = TYPE.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
            { "event": "mod_exhaustion",
              "condition": { "type": "neoorigins:constant", "value": false },
              "modifier": { "operation": "multiplication", "value": 2.0 },
              "_power_id": "test:aoe_own" }
            """).getAsJsonObject()).getOrThrow();
        assertEquals(2f, cfg.modifier().apply(1f), 1e-6f);
        var h = allow(cfg, new AtomicBoolean(true));
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);
        assertEquals(1f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f,
            "an open power_condition must not override the per-event condition");
        h.onRevoked(sp);
    }

    @Test
    void stackedModifiersKeepTheirOrderAcrossAFlip() {
        AtomicBoolean open = new AtomicBoolean(true);
        var add = allow(config("test:aoe_add", Event.MOD_EXHAUSTION, EntityCondition.alwaysTrue(),
            EntityAction.noop(), base -> base + 1f, 0), open);
        var times = holder(config("test:aoe_times", Event.MOD_EXHAUSTION, EntityCondition.alwaysTrue(),
            EntityAction.noop(), base -> base * 2f, 0), null, PowerHolder.ConditionMode.DENY);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        add.onGranted(sp);
        times.onGranted(sp);
        assertEquals(4f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f);

        open.set(false);
        assertEquals(2f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f);

        // (1+1)*2, not 1*2+1: the gated modifier was not moved to the end of the chain.
        open.set(true);
        assertEquals(4f, EventPowerIndex.dispatchModifier(sp, Event.MOD_EXHAUSTION, null, 1f), 1e-6f);
        add.onRevoked(sp);
        times.onRevoked(sp);
    }

    @Test
    void cooldownIsNotStartedWhileGated() {
        AtomicBoolean open = new AtomicBoolean(false);
        AtomicInteger fired = new AtomicInteger();
        var h = allow(counting("test:aoe_cd", fired, 20), open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);
        var data = sp.getData(OriginAttachments.originData());

        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(0, fired.get());
        assertEquals(0f, data.getCustomFloat("action_on_event_cd:test:aoe_cd", 0), 1e-6f,
            "a gated dispatch started the cooldown");

        open.set(true);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(1, fired.get());
        assertEquals(20f, data.getCustomFloat("action_on_event_cd:test:aoe_cd", 0), 1e-6f);

        // The timer only counts down on open-gate ticks.
        open.set(false);
        for (int i = 0; i < 5; i++) h.onTick(sp);
        assertEquals(20f, data.getCustomFloat("action_on_event_cd:test:aoe_cd", 0), 1e-6f);
        open.set(true);
        for (int i = 0; i < 5; i++) h.onTick(sp);
        assertEquals(15f, data.getCustomFloat("action_on_event_cd:test:aoe_cd", 0), 1e-6f);
        h.onRevoked(sp);
    }

    @Test
    void gateSurvivesRespawnLoginAndRegrant() {
        AtomicBoolean open = new AtomicBoolean(false);
        AtomicInteger fired = new AtomicInteger();
        var h = allow(counting("test:aoe_life", fired, 0), open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        h.onGranted(sp);
        PlayerLifecycle.respawn(h, sp);
        PlayerLifecycle.login(h, sp);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(0, fired.get(), "a re-grant from respawn or login dropped the gate");
        assertEquals(1, EventPowerIndex.handlerCount(sp, Event.BLOCK_BREAK), "re-grants stacked handlers");

        h.onRevoked(sp);
        h.onGranted(sp);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(0, fired.get());
        open.set(true);
        EventPowerIndex.dispatch(sp, Event.BLOCK_BREAK, null);
        assertEquals(1, fired.get());
        h.onRevoked(sp);
    }
}
