package com.cyberday1.neoorigins.effect;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * {@code neoorigins:suppression} — an origin-ability lock.
 *
 * <p>While it is held, {@link PowerHolder#onActivated} refuses every activation:
 * keybind powers, the {@code activate_power} action, and toggles in both
 * directions. Passive powers keep running; an author who wants one to stop gates
 * it with an inverted {@code has_effect} power condition, which already works.
 *
 * <p>A toggle that is already on when the effect lands is forced off rather than
 * left on and locked. Leaving it on would strand the player mid-transformation
 * with the one key that undoes it refused, and nothing on screen to explain why.
 *
 * <p>The amplifier carries no meaning and is reserved.
 */
public class SuppressionEffect extends MobEffect {

    /** Muted violet. Desaturated on purpose so it does not read as a buff. */
    private static final int COLOR = 0x5A4E7C;

    public SuppressionEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * Fires once per fresh application (a refresh of an already-held instance
     * goes through the update path instead), on whichever side ran
     * {@code addEffect} — hence the {@link ServerPlayer} narrowing, since power
     * state is server-owned.
     */
    @Override
    public void onEffectStarted(LivingEntity mob, int amplifier) {
        if (mob instanceof ServerPlayer player) {
            forceOffActiveToggles(player, ActiveOriginService.allPowers(player));
        }
    }

    /**
     * Turns off every toggle power the player currently has on, tearing each
     * one's effect down. Returns how many were flipped.
     *
     * <p>Split out from {@link #onEffectStarted} so the selection can be driven
     * directly in tests with a hand-built holder list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int forceOffActiveToggles(ServerPlayer player, List<PowerHolder<?>> holders) {
        int forced = 0;
        for (PowerHolder<?> holder : holders) {
            if (!(holder.type() instanceof AbstractTogglePower toggle)) continue;
            if (toggle.forceOff(player, holder.config())) forced++;
        }
        return forced;
    }
}
