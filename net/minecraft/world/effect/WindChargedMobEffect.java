package net.minecraft.world.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;

class WindChargedMobEffect extends MobEffect {
    protected WindChargedMobEffect(MobEffectCategory p_338347_, int p_338254_) {
        super(p_338347_, p_338254_, ParticleTypes.SMALL_GUST);
    }

    @Override
    public void onMobRemoved(LivingEntity p_338439_, int p_338875_, Entity.RemovalReason p_338258_) {
        if (p_338258_ == Entity.RemovalReason.KILLED && p_338439_.level() instanceof ServerLevel serverlevel) {
            double d2 = p_338439_.getX();
            double d0 = p_338439_.getY() + (double)(p_338439_.getBbHeight() / 2.0F);
            double d1 = p_338439_.getZ();
            float f = 3.0F + p_338439_.getRandom().nextFloat() * 2.0F;
            serverlevel.explode(
                p_338439_,
                null,
                AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR,
                d2,
                d0,
                d1,
                f,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.BREEZE_WIND_CHARGE_BURST
            );
        }
    }
}
