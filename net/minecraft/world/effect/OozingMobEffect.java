package net.minecraft.world.effect;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

class OozingMobEffect extends MobEffect {
    private static final int RADIUS_TO_CHECK_SLIMES = 2;
    public static final int SLIME_SIZE = 2;
    private final ToIntFunction<RandomSource> spawnedCount;

    protected OozingMobEffect(MobEffectCategory p_338567_, int p_338409_, ToIntFunction<RandomSource> p_338888_) {
        super(p_338567_, p_338409_, ParticleTypes.ITEM_SLIME);
        this.spawnedCount = p_338888_;
    }

    @VisibleForTesting
    protected static int numberOfSlimesToSpawn(int p_341016_, OozingMobEffect.NearbySlimes p_348591_, int p_341398_) {
        return p_341016_ < 1 ? p_341398_ : Mth.clamp(0, p_341016_ - p_348591_.count(p_341016_), p_341398_);
    }

    @Override
    public void onMobRemoved(LivingEntity p_338339_, int p_338421_, Entity.RemovalReason p_338677_) {
        if (p_338677_ == Entity.RemovalReason.KILLED) {
            int i = this.spawnedCount.applyAsInt(p_338339_.getRandom());
            Level level = p_338339_.level();
            int j = level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
            int k = numberOfSlimesToSpawn(j, OozingMobEffect.NearbySlimes.closeTo(p_338339_), i);

            for (int l = 0; l < k; l++) {
                this.spawnSlimeOffspring(p_338339_.level(), p_338339_.getX(), p_338339_.getY() + 0.5, p_338339_.getZ());
            }
        }
    }

    private void spawnSlimeOffspring(Level p_338724_, double p_338555_, double p_338811_, double p_338192_) {
        Slime slime = EntityType.SLIME.create(p_338724_);
        if (slime != null) {
            slime.setSize(2, true);
            slime.moveTo(p_338555_, p_338811_, p_338192_, p_338724_.getRandom().nextFloat() * 360.0F, 0.0F);
            p_338724_.addFreshEntity(slime);
        }
    }

    @FunctionalInterface
    protected interface NearbySlimes {
        int count(int p_348627_);

        static OozingMobEffect.NearbySlimes closeTo(LivingEntity p_348471_) {
            return p_352702_ -> {
                List<Slime> list = new ArrayList<>();
                p_348471_.level().getEntities(EntityType.SLIME, p_348471_.getBoundingBox().inflate(2.0), p_348647_ -> p_348647_ != p_348471_, list, p_352702_);
                return list.size();
            };
        }
    }
}
