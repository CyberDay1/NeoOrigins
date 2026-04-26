package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

class SaturationMobEffect extends InstantenousMobEffect {
    protected SaturationMobEffect(MobEffectCategory p_294221_, int p_295725_) {
        super(p_294221_, p_295725_);
    }

    @Override
    public boolean applyEffectTick(LivingEntity p_295892_, int p_296026_) {
        if (!p_295892_.level().isClientSide && p_295892_ instanceof Player player) {
            player.getFoodData().eat(p_296026_ + 1, 1.0F);
        }

        return true;
    }
}
