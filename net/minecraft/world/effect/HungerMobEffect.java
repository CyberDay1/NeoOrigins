package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

class HungerMobEffect extends MobEffect {
    protected HungerMobEffect(MobEffectCategory p_294845_, int p_296235_) {
        super(p_294845_, p_296235_);
    }

    @Override
    public boolean applyEffectTick(LivingEntity p_296407_, int p_296356_) {
        if (p_296407_ instanceof Player player) {
            player.causeFoodExhaustion(0.005F * (float)(p_296356_ + 1));
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int p_295391_, int p_294280_) {
        return true;
    }
}
