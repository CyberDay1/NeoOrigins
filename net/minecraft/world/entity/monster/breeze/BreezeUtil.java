package net.minecraft.world.entity.monster.breeze;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BreezeUtil {
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 50.0;

    public static Vec3 randomPointBehindTarget(LivingEntity p_316886_, RandomSource p_316867_) {
        int i = 90;
        float f = p_316886_.yHeadRot + 180.0F + (float)p_316867_.nextGaussian() * 90.0F / 2.0F;
        float f1 = Mth.lerp(p_316867_.nextFloat(), 4.0F, 8.0F);
        Vec3 vec3 = Vec3.directionFromRotation(0.0F, f).scale((double)f1);
        return p_316886_.position().add(vec3);
    }

    public static boolean hasLineOfSight(Breeze p_316785_, Vec3 p_316249_) {
        Vec3 vec3 = new Vec3(p_316785_.getX(), p_316785_.getY(), p_316785_.getZ());
        return p_316249_.distanceTo(vec3) > 50.0
            ? false
            : p_316785_.level().clip(new ClipContext(vec3, p_316249_, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p_316785_)).getType()
                == HitResult.Type.MISS;
    }
}
