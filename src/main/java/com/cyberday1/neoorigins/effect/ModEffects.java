package com.cyberday1.neoorigins.effect;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoOrigins-owned {@link MobEffect} registrations.
 *
 * <p>Everything else in the mod that touches {@code BuiltInRegistries.MOB_EFFECT}
 * is looking somebody else's effect up by id; this is the only place NeoOrigins
 * contributes one of its own.
 */
public final class ModEffects {

    private ModEffects() {}

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, NeoOrigins.MOD_ID);

    /**
     * {@code neoorigins:suppression} — blocks active, keybind and toggle power
     * activations for as long as it is held. Passive powers are untouched by
     * design; an author who wants a passive to stop gates it themselves with an
     * inverted {@code has_effect} power condition.
     */
    public static final DeferredHolder<MobEffect, SuppressionEffect> SUPPRESSION =
        MOB_EFFECTS.register("suppression", SuppressionEffect::new);

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
