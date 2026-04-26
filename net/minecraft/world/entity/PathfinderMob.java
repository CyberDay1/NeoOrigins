package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

public abstract class PathfinderMob extends Mob {
    protected static final float DEFAULT_WALK_TARGET_VALUE = 0.0F;

    protected PathfinderMob(EntityType<? extends PathfinderMob> p_21683_, Level p_21684_) {
        super(p_21683_, p_21684_);
    }

    public float getWalkTargetValue(BlockPos p_21693_) {
        return this.getWalkTargetValue(p_21693_, this.level());
    }

    public float getWalkTargetValue(BlockPos p_21688_, LevelReader p_21689_) {
        return 0.0F;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor p_21686_, MobSpawnType p_21687_) {
        return this.getWalkTargetValue(this.blockPosition(), p_21686_) >= 0.0F;
    }

    public boolean isPathFinding() {
        return !this.getNavigation().isDone();
    }

    public boolean isPanicking() {
        if (this.brain.hasMemoryValue(MemoryModuleType.IS_PANICKING)) {
            return this.brain.getMemory(MemoryModuleType.IS_PANICKING).isPresent();
        } else {
            for (WrappedGoal wrappedgoal : this.goalSelector.getAvailableGoals()) {
                if (wrappedgoal.isRunning() && wrappedgoal.getGoal() instanceof PanicGoal) {
                    return true;
                }
            }

            return false;
        }
    }

    protected boolean shouldStayCloseToLeashHolder() {
        return true;
    }

    @Override
    public void closeRangeLeashBehaviour(Entity p_352153_) {
        super.closeRangeLeashBehaviour(p_352153_);
        if (this.shouldStayCloseToLeashHolder() && !this.isPanicking()) {
            this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            float f = 2.0F;
            float f1 = this.distanceTo(p_352153_);
            Vec3 vec3 = new Vec3(p_352153_.getX() - this.getX(), p_352153_.getY() - this.getY(), p_352153_.getZ() - this.getZ())
                .normalize()
                .scale((double)Math.max(f1 - 2.0F, 0.0F));
            this.getNavigation().moveTo(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z, this.followLeashSpeed());
        }
    }

    @Override
    public boolean handleLeashAtDistance(Entity p_352071_, float p_352237_) {
        this.restrictTo(p_352071_.blockPosition(), 5);
        return true;
    }

    protected double followLeashSpeed() {
        return 1.0;
    }
}
