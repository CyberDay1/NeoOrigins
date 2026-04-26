package net.minecraft.world.effect;

import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

class InfestedMobEffect extends MobEffect {
    private final float chanceToSpawn;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected InfestedMobEffect(MobEffectCategory p_338776_, int p_338484_, float p_338378_, ToIntFunction<RandomSource> p_338750_) {
        super(p_338776_, p_338484_, ParticleTypes.INFESTED);
        this.chanceToSpawn = p_338378_;
        this.spawnedCount = p_338750_;
    }

    @Override
    public void onMobHurt(LivingEntity p_338481_, int p_338438_, DamageSource p_338894_, float p_338367_) {
        if (p_338481_.getRandom().nextFloat() <= this.chanceToSpawn) {
            int i = this.spawnedCount.applyAsInt(p_338481_.getRandom());

            for (int j = 0; j < i; j++) {
                this.spawnSilverfish(p_338481_.level(), p_338481_, p_338481_.getX(), p_338481_.getY() + (double)p_338481_.getBbHeight() / 2.0, p_338481_.getZ());
            }
        }
    }

    private void spawnSilverfish(Level p_338804_, LivingEntity p_341023_, double p_338557_, double p_338848_, double p_338562_) {
        Silverfish silverfish = EntityType.SILVERFISH.create(p_338804_);
        if (silverfish != null) {
            RandomSource randomsource = p_341023_.getRandom();
            float f = (float) (Math.PI / 2);
            float f1 = Mth.randomBetween(randomsource, (float) (-Math.PI / 2), (float) (Math.PI / 2));
            Vector3f vector3f = p_341023_.getLookAngle().toVector3f().mul(0.3F).mul(1.0F, 1.5F, 1.0F).rotateY(f1);
            silverfish.moveTo(p_338557_, p_338848_, p_338562_, p_338804_.getRandom().nextFloat() * 360.0F, 0.0F);
            silverfish.setDeltaMovement(new Vec3(vector3f));
            p_338804_.addFreshEntity(silverfish);
            silverfish.playSound(SoundEvents.SILVERFISH_HURT);
        }
    }
}
