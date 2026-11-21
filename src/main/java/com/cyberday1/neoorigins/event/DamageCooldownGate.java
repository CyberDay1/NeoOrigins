package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.mixin.LivingEntityLastHurtAccessor;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mirrors the damage-cooldown check in {@code LivingEntity.hurt}, which runs after
 * {@code LivingIncomingDamageEvent}: a hit it drops must not fire hit reactions.
 */
public final class DamageCooldownGate {

    private DamageCooldownGate() {}

    /** True when vanilla will drop this hit, at its current amount, inside the victim's damage cooldown. */
    public static boolean discards(LivingEntity victim, DamageSource source, float amount) {
        return discards(victim.damageCooldownTime, ((LivingEntityLastHurtAccessor) victim).neoorigins$getLastHurt(),
            source.is(DamageTypeTags.BYPASSES_COOLDOWN), vanillaScaled(victim, source, amount));
    }

    /** Vanilla's rule: inside the cooldown a hit no larger than the last is dropped; a larger one lands for the excess. */
    static boolean discards(int cooldownTicks, float lastHurt, boolean bypassesCooldown, float amount) {
        return cooldownTicks > 10 && !bypassesCooldown && amount <= lastHurt;
    }

    /** The scaling hurt applies between the event and the check. Shield blocking is not mirrored. */
    static float vanillaScaled(LivingEntity victim, DamageSource source, float amount) {
        if (amount < 0.0F) amount = 0.0F;
        if (source.is(DamageTypeTags.IS_FREEZING) && victim.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
            amount *= 5.0F;
        }
        if (source.is(DamageTypeTags.DAMAGES_HELMET) && !victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            amount *= 0.75F;
        }
        if (Float.isNaN(amount) || Float.isInfinite(amount)) amount = Float.MAX_VALUE;
        return amount;
    }
}
