package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ResetRaidStatus {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(p_259870_ -> p_259870_.point((p_340734_, p_340735_, p_340736_) -> {
                if (p_340734_.random.nextInt(20) != 0) {
                    return false;
                } else {
                    Brain<?> brain = p_340735_.getBrain();
                    Raid raid = p_340734_.getRaidAt(p_340735_.blockPosition());
                    if (raid == null || raid.isStopped() || raid.isLoss()) {
                        brain.setDefaultActivity(Activity.IDLE);
                        brain.updateActivityFromSchedule(p_340734_.getDayTime(), p_340734_.getGameTime());
                    }

                    return true;
                }
            }));
    }
}
