package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;

public class BreezeAttackEntitySensor extends NearestLivingEntitySensor<Breeze> {
    public static final int BREEZE_SENSOR_RADIUS = 24;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.copyOf(Iterables.concat(super.requires(), List.of(MemoryModuleType.NEAREST_ATTACKABLE)));
    }

    protected void doTick(ServerLevel p_312447_, Breeze p_312739_) {
        super.doTick(p_312447_, p_312739_);
        p_312739_.getBrain()
            .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
            .stream()
            .flatMap(Collection::stream)
            .filter(EntitySelector.NO_CREATIVE_OR_SPECTATOR)
            .filter(p_312759_ -> Sensor.isEntityAttackable(p_312739_, p_312759_))
            .findFirst()
            .ifPresentOrElse(
                p_312872_ -> p_312739_.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, p_312872_),
                () -> p_312739_.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE)
            );
    }

    @Override
    protected int radiusXZ() {
        return 24;
    }

    @Override
    protected int radiusY() {
        return 24;
    }
}
