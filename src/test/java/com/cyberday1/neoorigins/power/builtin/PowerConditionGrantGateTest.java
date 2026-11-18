package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import com.cyberday1.neoorigins.power.util.PowerFlight;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A top-level {@code power_condition} has to switch off what a power put on the
 * player, not only stop its tick. These powers attach a modifier or an infinite
 * effect, flight, gravity or a registry entry, and used to keep it for as long
 * as the gate stayed shut, so Max's in-water slowdown applied on land too.
 *
 * <p>Each test drives the real {@link PowerHolder#onTick} with an
 * {@code ALLOW}-mode gate the test flips, on a genuine {@link ServerPlayer}.
 */
class PowerConditionGrantGateTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "gated");

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

    private static void ticks(PowerHolder<?> holder, ServerPlayer sp, int n) {
        for (int i = 0; i < n; i++) {
            sp.tickCount++;
            holder.onTick(sp);
        }
    }

    @Test
    void modifyFlightSpeedFollowsTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new ModifyFlightSpeedPower(), """
            { "operation": "multiply_base", "value": 1.0 }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        assertEquals(0.10f, sp.getAbilities().getFlyingSpeed(), 1e-6f);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.05f, sp.getAbilities().getFlyingSpeed(), 1e-6f,
            "the flight speed stayed scaled with the power_condition shut");

        open.set(true);
        tick(holder, sp);
        assertEquals(0.10f, sp.getAbilities().getFlyingSpeed(), 1e-6f);
        holder.onRevoked(sp);
    }

    @Test
    void modifyLavaSpeedFollowsTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new ModifyLavaSpeedPower(), """
            { "operation": "addition", "value": 0.4 }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        var kind = NumericModifierRegistry.Kind.LAVA_SPEED;
        assertEquals(0.42, NumericModifierRegistry.applyByUuid(sp.getUUID(), kind, 0.02), 1e-6);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.02, NumericModifierRegistry.applyByUuid(sp.getUUID(), kind, 0.02), 1e-6,
            "the lava speed stayed boosted with the power_condition shut");

        open.set(true);
        tick(holder, sp);
        assertEquals(0.42, NumericModifierRegistry.applyByUuid(sp.getUUID(), kind, 0.02), 1e-6);
        holder.onRevoked(sp);
    }

    @Test
    void creativeFlightIsTakenAwayWithoutSwitchingTheToggleOff() {
        AtomicBoolean open = new AtomicBoolean(true);
        var power = new CreativeFlightPower();
        var holder = gated(power, "{}", open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        tick(holder, sp);
        assertTrue(PowerFlight.hasAny(sp), "the open gate should grant flight");
        sp.getAbilities().flying = true;

        open.set(false);
        tick(holder, sp);
        assertFalse(PowerFlight.hasAny(sp), "flight stayed on with the power_condition shut");
        assertFalse(sp.getAbilities().flying, "a survival player gated off mid-air should drop");
        assertFalse(power.isToggledOff(sp, holder.config(), ID), "the gate must not switch the toggle off");

        open.set(true);
        tick(holder, sp);
        assertTrue(PowerFlight.hasAny(sp), "flight did not come back when the gate opened");
    }

    @Test
    void aShutGateLeavesGameModeFlightAlone() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new CreativeFlightPower(), "{}", open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        tick(holder, sp);
        // What creative and spectator mode set; the power never touches it.
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;

        open.set(false);
        tick(holder, sp);
        assertFalse(PowerFlight.hasAny(sp));
        assertTrue(sp.getAbilities().mayfly);
        assertTrue(sp.getAbilities().flying, "a creative flyer must keep flying");
    }

    @Test
    void phantomFormDropsGravityAndFlightOnceWhileGated() {
        AtomicBoolean open = new AtomicBoolean(true);
        var power = new PhantomFormPower();
        var holder = gated(power, """
            { "invisibility": false }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        tick(holder, sp);
        assertTrue(sp.isNoGravity());
        assertTrue(PowerFlight.hasAny(sp));

        open.set(false);
        tick(holder, sp);
        assertFalse(sp.isNoGravity(), "no_gravity outlived the power_condition");
        assertFalse(PowerFlight.hasAny(sp));
        assertFalse(power.isToggledOff(sp, holder.config(), ID), "the gate must not switch the toggle off");
        // Taken off on the closing tick only: a gated player still takes fall damage.
        sp.fallDistance = 3.0F;
        tick(holder, sp);
        assertEquals(3.0F, sp.fallDistance, 1e-6f, "removeEffect ran again on a later shut tick");

        open.set(true);
        tick(holder, sp);
        assertTrue(sp.isNoGravity(), "phantom form did not come back when the gate opened");
        assertTrue(PowerFlight.hasAny(sp));
    }

    @Test
    void aToggledOffPhantomFormStaysOffWhenTheGateOpens() {
        AtomicBoolean open = new AtomicBoolean(false);
        var power = new PhantomFormPower();
        var holder = gated(power, """
            { "invisibility": false }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.getData(OriginAttachments.originData())
            .setPowerToggledOff(ID.toString(), PhantomFormPower.class.getName(), true);
        tick(holder, sp);
        open.set(true);
        tick(holder, sp);
        assertFalse(sp.isNoGravity());
        assertFalse(PowerFlight.hasAny(sp));
    }

    @Test
    void lessItemUseSlowdownComesOffWhileTheGateIsShut() throws Exception {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new LessItemUseSlowdownPower(), """
            { "item_type": "any", "speed_multiplier": 0.5 }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        // isUsingItem reads this flag; setting it directly avoids a held item and the use event.
        Method flag = LivingEntity.class.getDeclaredMethod("setLivingEntityFlag", int.class, boolean.class);
        flag.setAccessible(true);
        flag.invoke(sp, 1, true);
        assertTrue(sp.isUsingItem());
        tick(holder, sp);
        assertEquals(0.5, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9);

        open.set(true);
        tick(holder, sp);
        assertEquals(0.5, modifierTotal(sp, Attributes.MOVEMENT_SPEED), 1e-9);
    }

    @Test
    void slimeLevelHpFollowsTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new SlimeLevelHPPower(), """
            { "levels_per_hp": 10, "max_bonus_hp": 20 }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.experienceLevel = 30;
        ticks(holder, sp, 20);
        assertEquals(3.0, modifierTotal(sp, Attributes.MAX_HEALTH), 1e-9);

        open.set(false);
        ticks(holder, sp, 20);
        assertEquals(0.0, modifierTotal(sp, Attributes.MAX_HEALTH), 1e-9);

        open.set(true);
        ticks(holder, sp, 20);
        assertEquals(3.0, modifierTotal(sp, Attributes.MAX_HEALTH), 1e-9);
    }

    @Test
    void slimeMoistureArmourPenaltyComesOffWhileTheGateIsShut() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new SlimeMoisturePower(), "{}", open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        // The tick syncs the moisture bar to the client.
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        // Rain keeps the tick off the biome lookup, which the rig level can't answer.
        // From empty, the refill stays under the 10% penalty line for the whole test.
        Mockito.when(sp.level().isRainingAt(ArgumentMatchers.any(BlockPos.class))).thenReturn(true);
        SlimeMoisturePower.setMoisture(sp, 0.0F);
        tick(holder, sp);
        assertEquals(-4.0, modifierTotal(sp, Attributes.ARMOR), 1e-9);

        open.set(false);
        tick(holder, sp);
        assertEquals(0.0, modifierTotal(sp, Attributes.ARMOR), 1e-9,
            "the dry armour penalty stayed on with the power_condition shut");

        open.set(true);
        tick(holder, sp);
        assertEquals(-4.0, modifierTotal(sp, Attributes.ARMOR), 1e-9);
    }

    @Test
    void tamedAnimalBoostFollowsTheGateWithoutAFreeHeal() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new TamedAnimalBoostPower(), """
            { "health_bonus": 4.0, "speed_bonus": 0.1, "radius": 8.0 }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        var health = new AttributeInstance(Attributes.MAX_HEALTH, i -> { });
        var speed = new AttributeInstance(Attributes.MOVEMENT_SPEED, i -> { });
        Wolf wolf = Mockito.mock(Wolf.class);
        Mockito.when(wolf.getOwner()).thenReturn(sp);
        Mockito.when(wolf.getUUID()).thenReturn(UUID.randomUUID());
        Mockito.when(wolf.getAttribute(Attributes.MAX_HEALTH)).thenReturn(health);
        Mockito.when(wolf.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        Mockito.when(sp.level().getEntitiesOfClass(ArgumentMatchers.eq(Animal.class), ArgumentMatchers.any(AABB.class)))
            .thenReturn(List.of(wolf));
        sp.tickCount = 59;

        tick(holder, sp);
        assertEquals(1, health.getModifiers().size());
        assertEquals(1, speed.getModifiers().size());

        open.set(false);
        ticks(holder, sp, 60);
        assertTrue(health.getModifiers().isEmpty(), "the pet kept its boost with the power_condition shut");
        assertTrue(speed.getModifiers().isEmpty());

        open.set(true);
        ticks(holder, sp, 60);
        assertEquals(1, health.getModifiers().size(), "the boost did not come back when the gate opened");
        // Healed to the new maximum when first boosted, not again when the gate reopens.
        Mockito.verify(wolf, Mockito.times(1)).setHealth(ArgumentMatchers.anyFloat());
    }

    @Test
    void killLootDropsRulesFollowTheGate() {
        AtomicBoolean open = new AtomicBoolean(true);
        var holder = gated(new KillLootDropsPower(), """
            { "_power_id": "test:gated",
              "drops": [ { "entity_type": "minecraft:zombie", "item": "minecraft:bone" } ] }
            """, open);
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        assertEquals(1, KillLootDropsPower.activeRules(sp.getUUID()).size());

        open.set(false);
        tick(holder, sp);
        assertTrue(KillLootDropsPower.activeRules(sp.getUUID()).isEmpty(),
            "the drop rules stayed live with the power_condition shut");

        open.set(true);
        tick(holder, sp);
        assertEquals(1, KillLootDropsPower.activeRules(sp.getUUID()).size());
        holder.onRevoked(sp);
    }
}
