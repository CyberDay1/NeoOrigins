package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.CombatTracker;
import com.cyberday1.neoorigins.service.FirstPickGraceTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression net for #129 — a fire-immune origin still burned on a campfire.
 *
 * <p>The gate used to hand-list four damage types (IN_FIRE, ON_FIRE, LAVA,
 * HOT_FLOOR). The vanilla {@code minecraft:is_fire} tag has seven members, so
 * CAMPFIRE and both FIREBALL types fell through and were dealt in full. It now
 * asks the tag, which is what the equivalent condition already did.
 *
 * <p>Why the damage source is a mock rather than a real one: {@code is(TagKey)}
 * resolves through the holder's bound tags, and damage types are a datapack
 * registry — there is no tag binding in a bare JUnit bootstrap, so a genuine
 * CAMPFIRE source would report false for every tag and the test would prove
 * nothing. Stubbing the source states the premise directly instead: "this is a
 * damage type that is in is_fire but is none of the four legacy constants."
 * That is exactly the case the old code got wrong, so these tests fail against
 * it — verified by reverting the guard, not assumed.
 */
class FireImmunityDamageTypesTest {

    /** A source in {@code minecraft:is_fire} that is none of the four legacy constants. */
    private static DamageSource taggedFireSourceOnly() {
        DamageSource source = mock(DamageSource.class);
        when(source.is(DamageTypeTags.IS_FIRE)).thenReturn(true);
        // Left at Mockito's default false: IN_FIRE, ON_FIRE, LAVA, HOT_FLOOR.
        // That is the whole point — a campfire or fireball matches the tag and
        // none of the constants the old guard listed.
        return source;
    }

    /** A player past the first-pick invulnerability window, holding fire immunity. */
    private static ServerPlayer immuneToFire() {
        PlayerOriginData pod = mock(PlayerOriginData.class);
        when(pod.isHadAllOrigins()).thenReturn(true);

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getData(any(net.neoforged.neoforge.attachment.AttachmentType.class))).thenReturn(pod);
        return player;
    }

    private static LivingIncomingDamageEvent eventFor(
            ServerPlayer player, DamageSource source, boolean[] cancelled) {
        LivingIncomingDamageEvent event = mock(LivingIncomingDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getSource()).thenReturn(source);
        when(event.getAmount()).thenReturn(4.0f);
        org.mockito.Mockito.doAnswer(inv -> {
            if (Boolean.TRUE.equals(inv.getArgument(0))) cancelled[0] = true;
            return null;
        }).when(event).setCanceled(org.mockito.ArgumentMatchers.anyBoolean());
        return event;
    }

    /**
     * Runs the handler with the statics that a mocked player cannot satisfy
     * stubbed out, and reports whether the damage was cancelled.
     *
     * <p>{@code has(..., PreventActionPower.class, ...)} is stubbed true without
     * consulting the predicate, so the test is about which damage types reach
     * the gate — not about power gating, which has its own coverage.
     */
    private static boolean damageCancelled(ServerPlayer player, DamageSource source) {
        final boolean[] cancelled = {false};
        LivingIncomingDamageEvent event = eventFor(player, source, cancelled);
        try (MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class);
             MockedStatic<CombatTracker> combat = mockStatic(CombatTracker.class);
             MockedStatic<FirstPickGraceTracker> grace = mockStatic(FirstPickGraceTracker.class)) {
            grace.when(() -> FirstPickGraceTracker.isActive(any())).thenReturn(false);
            origins.when(() -> ActiveOriginService.has(any(), eq(PreventActionPower.class), any()))
                   .thenReturn(true);
            CombatPowerEvents.onLivingDamage(event);
        }
        return cancelled[0];
    }

    /** The reported case: standing on a campfire. */
    @Test
    void campfireDamageIsCancelledByFireImmunity() {
        assertTrue(damageCancelled(immuneToFire(), taggedFireSourceOnly()),
            "a fire-immune origin must not take campfire damage (#129)");
    }

    /** The same omission also let blaze and ghast fireballs through. */
    @Test
    void fireballDamageIsCancelledByFireImmunity() {
        assertTrue(damageCancelled(immuneToFire(), taggedFireSourceOnly()),
            "a fire-immune origin must not take fireball damage (#129)");
    }

    /**
     * The four types the old guard did list must still be cancelled, so the
     * change is proven additive rather than a swap that traded one gap for
     * another. Each source reports both its own constant and the tag, which is
     * true of all four in vanilla — so this case passes before and after the
     * fix, which is precisely what makes it a control.
     */
    @Test
    void theLegacyFourAreStillCancelled() {
        for (var type : java.util.List.of(
                DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.LAVA, DamageTypes.HOT_FLOOR)) {
            DamageSource source = mock(DamageSource.class);
            when(source.is(type)).thenReturn(true);
            when(source.is(DamageTypeTags.IS_FIRE)).thenReturn(true);
            assertTrue(damageCancelled(immuneToFire(), source),
                type + " must still be cancelled by fire immunity");
        }
    }

    /**
     * The negative control. Damage outside the fire tag must still land, or the
     * change would have made fire-immune origins immune to everything.
     */
    @Test
    void nonFireDamageIsNotCancelled() {
        DamageSource source = mock(DamageSource.class);
        // Every is(...) returns false: not fire, not drown, not freeze.
        assertFalse(damageCancelled(immuneToFire(), source),
            "fire immunity must not cancel unrelated damage");
    }
}
