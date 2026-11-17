package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A top-level {@code power_condition} has to switch off what a power put on the
 * player, not only stop its tick. These powers attach a modifier or an infinite
 * effect and used to keep it for as long as the gate stayed shut, so Max's
 * in-water slowdown applied on land too.
 *
 * <p>Each test drives the real {@link PowerHolder#onTick} with an
 * {@code ALLOW}-mode gate the test flips, on a genuine {@link ServerPlayer}.
 */
class PowerConditionGrantGateTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "gated");

    private static <C extends PowerConfiguration> PowerHolder<C> gated(PowerType<C> type, String json,
                                                                       AtomicBoolean open) {
        C config = type.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json).getAsJsonObject())
            .getOrThrow();
        return new PowerHolder<>(ID, type, config, Component.empty(), Component.empty(), false,
            p -> open.get(), PowerHolder.ConditionMode.ALLOW);
    }

    /** Five ticks, so the 5-tick cadence some powers use is crossed exactly once. */
    private static void tick(PowerHolder<?> holder, ServerPlayer sp) {
        for (int i = 0; i < 5; i++) {
            sp.tickCount++;
            holder.onTick(sp);
        }
    }

    private static double modifierTotal(ServerPlayer sp, Holder<Attribute> attribute) {
        var instance = sp.getAttribute(attribute);
        return instance == null ? 0.0
            : instance.getModifiers().stream()
                .filter(m -> "neoorigins".equals(m.id().getNamespace()))
                .mapToDouble(m -> m.amount()).sum();
    }

    @Test
    void attributeModifierComesOffWhileTheGateIsShut() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new AttributeModifierPower(), """
            { "attribute": "minecraft:generic.movement_speed", "amount": -0.3, "operation": "add_multiplied_total" }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        assertEquals(-0.3, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9,
            "the modifier stayed on with the power_condition shut");

        open.set(true);
        tick(holder, sp);
        assertEquals(-0.3, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9,
            "the modifier did not come back when the gate opened");
    }

    @Test
    void sizeScalingFollowsTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new SizeScalingPower(), """
            { "scale": 0.5, "modify_reach": false }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        assertEquals(-0.5, modifierTotal(sp, Attributes.SCALE), 1e-9);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.SCALE), 1e-9);

        open.set(true);
        tick(holder, sp);
        assertEquals(-0.5, modifierTotal(sp, Attributes.SCALE), 1e-9);
    }

    @Test
    void persistentEffectIsClearedWhileTheGateIsShut() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new PersistentEffectPower(), """
            { "effect": "minecraft:slow_falling" }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        // Adding an effect sends the client a packet naming the player, and the rig
        // player has neither a connection nor an entity id.
        sp.connection = org.mockito.Mockito.mock(net.minecraft.server.network.ServerGamePacketListenerImpl.class);
        sp.setId(1);
        holder.onGranted(sp);
        tick(holder, sp);
        assertTrue(sp.hasEffect(MobEffects.SLOW_FALLING), "the open gate should apply the effect");

        open.set(false);
        tick(holder, sp);
        assertFalse(sp.hasEffect(MobEffects.SLOW_FALLING),
            "an infinite effect outlived its power_condition");
    }

    @Test
    void preventSwimWalkSpeedFollowsTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new PreventActionPower(), """
            { "action": "swim" }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        tick(holder, sp);
        assertEquals(1.0, modifierTotal(sp, Attributes.WATER_MOVEMENT_EFFICIENCY), 1e-9,
            "a player who can't swim should walk the bottom at land speed");

        open.set(false);
        tick(holder, sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.WATER_MOVEMENT_EFFICIENCY), 1e-9);

        open.set(true);
        tick(holder, sp);
        holder.onRevoked(sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.WATER_MOVEMENT_EFFICIENCY), 1e-9,
            "the walk-speed modifier outlived the power");
    }

    @Test
    void preventSwimCapabilityFollowsActiveWhen() {
        var power = new PreventActionPower();
        var config = power.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
            { "action": "swim", "active_when": "not_sneaking" }
            """).getAsJsonObject()).getOrThrow();
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        assertTrue(power.capabilities(sp, config).contains(PreventActionPower.PREVENT_SWIM));
        sp.setShiftKeyDown(true);
        assertFalse(power.capabilities(sp, config).contains(PreventActionPower.PREVENT_SWIM),
            "active_when must switch the swim block off for the client too");
        assertTrue(power.capabilities(config).contains(PreventActionPower.PREVENT_SWIM),
            "the static set must still bound the player-aware one");
    }
}
