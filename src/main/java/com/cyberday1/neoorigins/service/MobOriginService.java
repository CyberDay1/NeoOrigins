package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.mob_origin.MobOrigin;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mob-side analogue of {@link ActiveOriginService#applyOriginPowers}. Walks a
 * mob origin's power list and applies/removes only the powers that explicitly
 * support mobs ({@code PowerType.appliesToMobs()}), via the
 * {@code applyToMob/removeFromMob} hooks — the {@code ServerPlayer}-typed
 * lifecycle methods cannot be used for a non-player entity.
 */
public final class MobOriginService {

    private MobOriginService() {}

    /**
     * Transition a mob from {@code oldOriginId} to {@code newOriginId}.
     * Either may be {@code null} (no origin). Powers whose type does not
     * support mobs are silently skipped.
     */
    public static void applyMobOriginPowers(LivingEntity mob,
                                            ResourceLocation oldOriginId,
                                            ResourceLocation newOriginId) {
        if (oldOriginId != null) {
            MobOrigin old = MobOriginDataManager.INSTANCE.getMobOrigin(oldOriginId);
            if (old != null) {
                for (ResourceLocation powerId : old.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null && holder.appliesToMobs()) {
                        try {
                            holder.removeFromMob(mob);
                        } catch (RuntimeException e) {
                            NeoOrigins.LOGGER.error("[mob-origin] removeFromMob failed for {} on {}",
                                powerId, mob.getType(), e);
                        }
                    }
                }
            }
        }
        if (newOriginId != null) {
            MobOrigin neo = MobOriginDataManager.INSTANCE.getMobOrigin(newOriginId);
            if (neo != null) {
                for (ResourceLocation powerId : neo.powers()) {
                    PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                    if (holder != null && holder.appliesToMobs()) {
                        try {
                            holder.applyToMob(mob);
                        } catch (RuntimeException e) {
                            NeoOrigins.LOGGER.error("[mob-origin] applyToMob failed for {} on {}",
                                powerId, mob.getType(), e);
                        }
                    }
                }
            }
        }
    }
}
