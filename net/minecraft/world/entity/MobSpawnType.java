package net.minecraft.world.entity;

public enum MobSpawnType {
    NATURAL,
    CHUNK_GENERATION,
    SPAWNER,
    STRUCTURE,
    BREEDING,
    MOB_SUMMONED,
    JOCKEY,
    EVENT,
    CONVERSION,
    REINFORCEMENT,
    TRIGGERED,
    BUCKET,
    SPAWN_EGG,
    COMMAND,
    DISPENSER,
    PATROL,
    TRIAL_SPAWNER;

    public static boolean isSpawner(MobSpawnType p_312682_) {
        return p_312682_ == SPAWNER || p_312682_ == TRIAL_SPAWNER;
    }

    public static boolean ignoresLightRequirements(MobSpawnType p_311814_) {
        return p_311814_ == TRIAL_SPAWNER;
    }
}
