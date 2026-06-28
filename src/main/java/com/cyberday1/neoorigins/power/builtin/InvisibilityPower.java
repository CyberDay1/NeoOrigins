package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Set;

/**
 * Native {@code neoorigins:invisibility} power — a passive that keeps the player
 * invisible while active and, with {@code render_armor: false}, also hides their
 * worn armor for true invisibility (rather than the vanilla "floating armor on an
 * invisible body" look).
 *
 * <p>This is the native replacement for the old compat path that rewrote
 * {@code origins:invisibility} into a {@code neoorigins:status_effect}: that path
 * dropped Apoli's {@code render_armor} field entirely. The compat layer now just
 * MAPS {@code origins:}/{@code apace:invisibility} onto this type, carrying
 * {@code render_armor} through (see {@code OriginsPowerTranslator.translateInvisibility}).
 *
 * <p><b>Invisibility</b> rides the vanilla {@link MobEffects#INVISIBILITY} effect,
 * re-applied with a SHORT duration every tick (mirrors the persistent-effect
 * idiom): server-authoritative and synced to every client as a normal effect, so
 * other players see the body vanish for free. Because the effect is applied in
 * {@link #onTick} — which {@code PowerHolder.onTick} skips while the top-level
 * {@code power_condition} gate is unsatisfied — and the applied duration is only a
 * few ticks, a condition-gated invisibility power switches off (the effect lapses
 * almost immediately) when its condition stops holding, exactly like every other
 * gated passive. (A long apply duration would have left the player invisible for
 * the remainder of that timer after the gate closed.)
 *
 * <p><b>Armor hiding</b> can't ride a vanilla mechanism — vanilla renders armor on
 * invisible entities. When {@code render_armor} is false this power emits the
 * {@link #CAP_HIDE_ARMOR} capability tag while granted; that tag is broadcast to
 * tracking clients alongside the morph state and read by the client armor-layer
 * mixin ({@code HumanoidArmorLayerMixin}). The mixin hides armor only when the
 * player carries this flag AND is currently invisible — so armor is hidden only
 * for players made invisible by THIS power with {@code render_armor:false} (never
 * for a vanilla potion / other source), and reappears the instant the
 * {@code power_condition} gate stops holding and the invisibility effect lapses
 * (the live {@code isInvisible()} check does the condition gating, so the flag
 * itself only has to track grant/revoke/toggle, not every condition tick).
 */
public class InvisibilityPower extends PowerType<InvisibilityPower.Config> {

    /** Capability tag emitted while the power is active with {@code render_armor:false}. */
    public static final String CAP_HIDE_ARMOR = "invisibility_hide_armor";

    // The effect is re-applied every onTick with a SHORT duration so that the
    // moment the top-level power_condition gate stops holding — at which point
    // PowerHolder.onTick stops dispatching to us — the already-applied effect
    // lapses within a tick or two instead of riding out a long timer. (The old
    // 300-tick / 15s apply meant a condition-gated invisibility stayed on for up
    // to 15s after the condition went false, since the effect just ticked down on
    // its own.) Unlike PersistentEffectPower — whose condition lives INSIDE its
    // own config so its onTick still runs and can clearEffects when false — this
    // power is gated purely by the external power_condition, so it can't rely on a
    // "condition went false" callback: the short auto-lapsing duration is what
    // gives it the same instant-off behaviour. onRevoked still removes the effect
    // outright for the grant/toggle path.
    private static final int APPLY_DURATION = 4;

    public record Config(boolean renderArmor) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("render_armor", true).forGetter(Config::renderArmor)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        // Refresh every tick so the effect persists while the gate holds, but
        // expires almost immediately once the gate closes and dispatch stops.
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, APPLY_DURATION, 0, true, false));
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        player.removeEffect(MobEffects.INVISIBILITY);
    }

    /**
     * Emit the armor-hide tag only when the author asked to hide armor. The tag is
     * collected into the player's active-capability set (condition-gated by
     * {@code PowerHolder.hasCapability}) and broadcast to tracking clients, where
     * the armor-layer mixin consumes it.
     */
    @Override
    public Set<String> capabilities(Config config) {
        return config.renderArmor() ? Set.of() : Set.of(CAP_HIDE_ARMOR);
    }
}
