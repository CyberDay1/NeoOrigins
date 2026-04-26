package net.minecraft.world.entity.monster.breeze;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class Slide extends Behavior<Breeze> {
    public Slide() {
        super(
            Map.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_JUMP_COOLDOWN,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_SHOOT,
                MemoryStatus.VALUE_ABSENT
            )
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel p_311853_, Breeze p_311894_) {
        return p_311894_.onGround() && !p_311894_.isInWater() && p_311894_.getPose() == Pose.STANDING;
    }

    protected void start(ServerLevel p_312325_, Breeze p_312534_, long p_311789_) {
        LivingEntity livingentity = p_312534_.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (livingentity != null) {
            boolean flag = p_312534_.withinInnerCircleRange(livingentity.position());
            Vec3 vec3 = null;
            if (flag) {
                Vec3 vec31 = DefaultRandomPos.getPosAway(p_312534_, 5, 5, livingentity.position());
                if (vec31 != null
                    && BreezeUtil.hasLineOfSight(p_312534_, vec31)
                    && livingentity.distanceToSqr(vec31.x, vec31.y, vec31.z) > livingentity.distanceToSqr(p_312534_)) {
                    vec3 = vec31;
                }
            }

            if (vec3 == null) {
                vec3 = p_312534_.getRandom().nextBoolean()
                    ? BreezeUtil.randomPointBehindTarget(livingentity, p_312534_.getRandom())
                    : randomPointInMiddleCircle(p_312534_, livingentity);
            }

            p_312534_.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(vec3), 0.6F, 1));
        }
    }

    private static Vec3 randomPointInMiddleCircle(Breeze p_311931_, LivingEntity p_312413_) {
        Vec3 vec3 = p_312413_.position().subtract(p_311931_.position());
        double d0 = vec3.length() - Mth.lerp(p_311931_.getRandom().nextDouble(), 8.0, 4.0);
        Vec3 vec31 = vec3.normalize().multiply(d0, d0, d0);
        return p_311931_.position().add(vec31);
    }
}
