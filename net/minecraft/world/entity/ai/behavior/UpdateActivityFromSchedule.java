package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;

public class UpdateActivityFromSchedule {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(p_259429_ -> p_259429_.point((p_340748_, p_340749_, p_340750_) -> {
                p_340749_.getBrain().updateActivityFromSchedule(p_340748_.getDayTime(), p_340748_.getGameTime());
                return true;
            }));
    }
}
