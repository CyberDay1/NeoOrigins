package net.minecraft.world.entity.ai.sensing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class MobSensor<T extends LivingEntity> extends Sensor<T> {
    private final BiPredicate<T, LivingEntity> mobTest;
    private final Predicate<T> readyTest;
    private final MemoryModuleType<Boolean> toSet;
    private final int memoryTimeToLive;

    public MobSensor(int p_316590_, BiPredicate<T, LivingEntity> p_316273_, Predicate<T> p_316373_, MemoryModuleType<Boolean> p_316764_, int p_316209_) {
        super(p_316590_);
        this.mobTest = p_316273_;
        this.readyTest = p_316373_;
        this.toSet = p_316764_;
        this.memoryTimeToLive = p_316209_;
    }

    @Override
    protected void doTick(ServerLevel p_316535_, T p_316183_) {
        if (!this.readyTest.test(p_316183_)) {
            this.clearMemory(p_316183_);
        } else {
            this.checkForMobsNearby(p_316183_);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES);
    }

    public void checkForMobsNearby(T p_316143_) {
        Optional<List<LivingEntity>> optional = p_316143_.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (!optional.isEmpty()) {
            boolean flag = optional.get().stream().anyMatch(p_316324_ -> this.mobTest.test(p_316143_, p_316324_));
            if (flag) {
                this.mobDetected(p_316143_);
            }
        }
    }

    public void mobDetected(T p_316813_) {
        p_316813_.getBrain().setMemoryWithExpiry(this.toSet, true, (long)this.memoryTimeToLive);
    }

    public void clearMemory(T p_316737_) {
        p_316737_.getBrain().eraseMemory(this.toSet);
    }
}
