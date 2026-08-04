package com.cyberday1.neoorigins.effect;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the {@code neoorigins:suppression} activation gate.
 *
 * <p>The gate lives in {@link PowerHolder#onActivated} because that is the one
 * method every activation path funnels through: the keybind packet, the
 * {@code activate_power} action, and the compat loader's programmatic fire. The
 * obvious-looking alternative — guarding {@link AbstractActivePower#onActivated}
 * next to the cooldown and hunger checks — is wrong, because
 * {@link AbstractTogglePower} extends {@link PowerType} directly and would sail
 * straight past it. {@link #togglePowersAreNotActivePowers()} pins that down so a
 * later refactor cannot quietly make the wrong place look right.
 *
 * <p>Sitting there is also why the feedback is split in two. Every path refuses,
 * but only {@link PowerHolder#onActivatedByKeypress} says so out loud, because
 * {@code activate_power} and continuous keybinds both reach the plain
 * {@link PowerHolder#onActivated} and can repeat every tick.
 */
class SuppressionGateTest {

    private record Cfg() implements PowerConfiguration {}

    /** Counts how many times the dispatch actually reached the power type. */
    private static final class ProbePower extends PowerType<Cfg> {
        int activations = 0;
        @Override public Codec<Cfg> codec() { return Codec.unit(Cfg::new); }
        @Override public boolean isActivePower() { return true; }
        @Override public void onActivated(ServerPlayer player, Cfg config) { activations++; }
    }

    /**
     * A toggle whose state and teardown are held in fields, so the force-off
     * sweep can be driven without a bound attachment registry.
     */
    private static final class ProbeToggle extends AbstractTogglePower<Cfg> {
        boolean off;
        int removals = 0;
        ProbeToggle(boolean off) { this.off = off; }
        @Override public Codec<Cfg> codec() { return Codec.unit(Cfg::new); }
        @Override public boolean isToggledOff(ServerPlayer player, Cfg config) { return off; }
        @Override protected void setToggledOff(ServerPlayer player, Cfg config, boolean value) { off = value; }
        @Override protected void tickEffect(ServerPlayer player, Cfg config) {}
        @Override protected void removeEffect(ServerPlayer player, Cfg config) { removals++; }
    }

    private static <C extends PowerConfiguration> PowerHolder<C> holder(String id, PowerType<C> type, C config) {
        return new PowerHolder<>(ResourceLocation.fromNamespaceAndPath("neoorigins", id),
            type, config, Component.empty(), Component.empty());
    }

    private static ServerPlayer player(boolean suppressed) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.hasEffect(ModEffects.SUPPRESSION)).thenReturn(suppressed);
        return player;
    }

    @Test
    void suppressedActivationNeverReachesThePowerType() {
        ServerPlayer player = player(true);
        ProbePower probe = new ProbePower();

        holder("probe", probe, new Cfg()).onActivated(player);

        assertEquals(0, probe.activations, "a suppressed activation must not reach the power type");
    }

    @Test
    void unsuppressedActivationReachesThePowerType() {
        ServerPlayer player = player(false);
        ProbePower probe = new ProbePower();

        holder("probe", probe, new Cfg()).onActivated(player);

        assertEquals(1, probe.activations, "an unsuppressed activation must run normally");
    }

    /**
     * A key press earns feedback, unlike the cooldown abort next to it. A player
     * can see a cooldown on the HUD; a player who does not know they have been
     * suppressed would read a silent abort as the mod being broken.
     */
    @Test
    void aRefusedKeypressTellsThePlayerWhyOnTheActionBar() {
        ServerPlayer player = player(true);

        holder("probe", new ProbePower(), new Cfg()).onActivatedByKeypress(player);

        verify(player).displayClientMessage(any(Component.class), eq(true));
        verify(player).playNotifySound(any(), eq(SoundSource.PLAYERS), anyFloat(), anyFloat());
    }

    /**
     * The other half of the split: {@code activate_power} and continuous keybinds
     * come through the plain entry point and can repeat every tick, so their
     * refusal says nothing. It still refuses — only the feedback is conditional.
     */
    @Test
    void aRefusedScriptedActivationSaysNothing() {
        ServerPlayer player = player(true);
        ProbePower probe = new ProbePower();

        holder("probe", probe, new Cfg()).onActivated(player);

        assertEquals(0, probe.activations, "a suppressed activation must not reach the power type");
        verify(player, never()).displayClientMessage(any(Component.class), eq(true));
        verify(player, never()).playNotifySound(any(), any(), anyFloat(), anyFloat());
    }

    /** The refusal is universal; only the message is not. */
    @Test
    void aSuppressedKeypressStillNeverReachesThePowerType() {
        ServerPlayer player = player(true);
        ProbePower probe = new ProbePower();

        holder("probe", probe, new Cfg()).onActivatedByKeypress(player);

        assertEquals(0, probe.activations, "a suppressed keypress must not reach the power type either");
    }

    /** An allowed keypress runs, and stays quiet on the way through. */
    @Test
    void anAllowedKeypressRunsAndSaysNothing() {
        ServerPlayer player = player(false);
        ProbePower probe = new ProbePower();

        holder("probe", probe, new Cfg()).onActivatedByKeypress(player);

        assertEquals(1, probe.activations, "an unsuppressed keypress must run normally");
        verify(player, never()).displayClientMessage(any(Component.class), eq(true));
        verify(player, never()).playNotifySound(any(), any(), anyFloat(), anyFloat());
    }

    @Test
    void anAllowedActivationSaysNothing() {
        ServerPlayer player = player(false);

        holder("probe", new ProbePower(), new Cfg()).onActivated(player);

        verify(player, never()).displayClientMessage(any(Component.class), eq(true));
        verify(player, never()).playNotifySound(any(), any(), anyFloat(), anyFloat());
    }

    /** The whole reason the gate is not in {@code AbstractActivePower}. */
    @Test
    void suppressedToggleKeypressDoesNothing() {
        ServerPlayer player = player(true);
        ProbeToggle toggle = new ProbeToggle(false);

        holder("toggle", toggle, new Cfg()).onActivatedByKeypress(player);

        assertFalse(toggle.off, "a suppressed keypress must not flip the toggle at all");
        assertEquals(0, toggle.removals, "a suppressed keypress must not run the toggle's teardown");
    }

    @Test
    void togglePowersAreNotActivePowers() {
        assertFalse(AbstractActivePower.class.isAssignableFrom(AbstractTogglePower.class),
            "AbstractTogglePower is not an AbstractActivePower, so a guard placed in "
                + "AbstractActivePower.onActivated would leave every toggle firing while suppressed");
    }

    @Test
    void applyingSuppressionForcesOnTogglesOff() {
        ServerPlayer player = player(true);
        ProbeToggle on = new ProbeToggle(false);
        ProbeToggle alreadyOff = new ProbeToggle(true);
        ProbePower passive = new ProbePower();

        int forced = SuppressionEffect.forceOffActiveToggles(player, List.of(
            holder("on", on, new Cfg()),
            holder("already_off", alreadyOff, new Cfg()),
            holder("not_a_toggle", passive, new Cfg())));

        assertEquals(1, forced, "only the toggle that was on should be flipped");
        assertTrue(on.off, "a toggle that was on must be forced off, not left on and locked");
        assertEquals(1, on.removals, "forcing a toggle off must tear its effect down");
        assertEquals(0, alreadyOff.removals, "a toggle that was already off must be left alone");
    }

    @Test
    void isSuppressedTracksTheEffectAndToleratesNoPlayer() {
        assertTrue(PowerSuppression.isSuppressed(player(true)));
        assertFalse(PowerSuppression.isSuppressed(player(false)));
        assertFalse(PowerSuppression.isSuppressed(null));
    }
}
