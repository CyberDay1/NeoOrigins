package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, NeoOrigins.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CobwebProjectileEntity>> COBWEB_PROJECTILE =
        ENTITY_TYPES.register("cobweb_projectile", () ->
            EntityType.Builder.<CobwebProjectileEntity>of(CobwebProjectileEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("cobweb_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<HomingProjectile>> HOMING_PROJECTILE =
        ENTITY_TYPES.register("homing_projectile", () ->
            EntityType.Builder.<HomingProjectile>of(HomingProjectile::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("homing_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicOrbProjectile>> MAGIC_ORB =
        ENTITY_TYPES.register("magic_orb", () ->
            EntityType.Builder.<MagicOrbProjectile>of(MagicOrbProjectile::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("magic_orb"));

    public static final DeferredHolder<EntityType<?>, EntityType<LingeringAreaEntity>> LINGERING_AREA =
        ENTITY_TYPES.register("lingering_area", () ->
            EntityType.Builder.<LingeringAreaEntity>of(LingeringAreaEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(8)
                .updateInterval(20)
                .build("lingering_area"));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackHoleVfxEntity>> BLACK_HOLE =
        ENTITY_TYPES.register("black_hole", () ->
            EntityType.Builder.<BlackHoleVfxEntity>of(BlackHoleVfxEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(10)
                .build("black_hole"));

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoVfxEntity>> TORNADO =
        ENTITY_TYPES.register("tornado", () ->
            EntityType.Builder.<TornadoVfxEntity>of(TornadoVfxEntity::new, MobCategory.MISC)
                .sized(1.0F, 4.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .build("tornado"));

    // Registry id stays "sword_rain" (saved entities + back-compat); the Java
    // type/field are generic since the rain can spawn any projectile, not swords.
    public static final DeferredHolder<EntityType<?>, EntityType<ProjectileRainVfxEntity>> PROJECTILE_RAIN =
        ENTITY_TYPES.register("sword_rain", () ->
            EntityType.Builder.<ProjectileRainVfxEntity>of(ProjectileRainVfxEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(24)
                .updateInterval(10)
                .build("sword_rain"));

    // A single thrown spectral blade (wuxia "flying sword"). Flies under real
    // physics; its landing point seeds spawn_projectile_rain (origin:"impact").
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSwordProjectile>> THROWN_SWORD =
        ENTITY_TYPES.register("thrown_sword", () ->
            EntityType.Builder.<ThrownSwordProjectile>of(ThrownSwordProjectile::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(8)
                .updateInterval(10)
                .build("thrown_sword"));

    public static final DeferredHolder<EntityType<?>, EntityType<TelegraphVfxEntity>> TELEGRAPH =
        ENTITY_TYPES.register("telegraph", () ->
            EntityType.Builder.<TelegraphVfxEntity>of(TelegraphVfxEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(24)
                .updateInterval(10)
                .build("telegraph"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
