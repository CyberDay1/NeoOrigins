package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ExplosionDamageCalculator {
    public Optional<Float> getBlockExplosionResistance(Explosion p_46099_, BlockGetter p_46100_, BlockPos p_46101_, BlockState p_46102_, FluidState p_46103_) {
        return p_46102_.isAir() && p_46103_.isEmpty()
            ? Optional.empty()
            : Optional.of(Math.max(p_46102_.getExplosionResistance(p_46100_, p_46101_, p_46099_), p_46103_.getExplosionResistance(p_46100_, p_46101_, p_46099_)));
    }

    public boolean shouldBlockExplode(Explosion p_46094_, BlockGetter p_46095_, BlockPos p_46096_, BlockState p_46097_, float p_46098_) {
        return true;
    }

    public boolean shouldDamageEntity(Explosion p_314652_, Entity p_314454_) {
        return true;
    }

    public float getKnockbackMultiplier(Entity p_340973_) {
        return 1.0F;
    }

    public float getEntityDamageAmount(Explosion p_311793_, Entity p_311929_) {
        float f = p_311793_.radius() * 2.0F;
        Vec3 vec3 = p_311793_.center();
        double d0 = Math.sqrt(p_311929_.distanceToSqr(vec3)) / (double)f;
        double d1 = (1.0 - d0) * (double)Explosion.getSeenPercent(vec3, p_311929_);
        return (float)((d1 * d1 + d1) / 2.0 * 7.0 * (double)f + 1.0);
    }
}
