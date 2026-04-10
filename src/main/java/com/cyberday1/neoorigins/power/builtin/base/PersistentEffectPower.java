package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Base class for powers that keep a permanent status effect on the player
 * by re-applying it before it expires. Always active — does not take a skill slot.
 *
 * <p>Subclasses provide the MobEffect and amplifier via {@link #effect} and
 * {@link #amplifier}. The effect is reapplied if missing or below 210 ticks
 * remaining, and removed when revoked.
 */
public abstract class PersistentEffectPower<C extends PowerConfiguration> extends PowerType<C> {

    private static final int APPLY_DURATION = 300;
    private static final int REFRESH_THRESHOLD = 210;

    protected abstract Holder<MobEffect> effect(C config);

    protected int amplifier(C config) { return 0; }

    @Override
    public void onTick(ServerPlayer player, C config) {
        Holder<MobEffect> eff = effect(config);
        var existing = player.getEffect(eff);
        if (existing == null || existing.getDuration() < REFRESH_THRESHOLD) {
            player.addEffect(new MobEffectInstance(eff, APPLY_DURATION, amplifier(config), true, false));
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, C config) {
        player.removeEffect(effect(config));
    }
}
