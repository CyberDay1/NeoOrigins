package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class AbsorptionMobEffect extends MobEffect {
    protected AbsorptionMobEffect(MobEffectCategory p_294899_, int p_295631_) {
        super(p_294899_, p_295631_);
    }

    @Override
    public boolean applyEffectTick(LivingEntity p_294484_, int p_294672_) {
        return p_294484_.getAbsorptionAmount() > 0.0F || p_294484_.level().isClientSide;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int p_295357_, int p_294523_) {
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity p_294820_, int p_295222_) {
        super.onEffectStarted(p_294820_, p_295222_);
        p_294820_.setAbsorptionAmount(Math.max(p_294820_.getAbsorptionAmount(), (float)(4 * (1 + p_295222_))));
    }
}
