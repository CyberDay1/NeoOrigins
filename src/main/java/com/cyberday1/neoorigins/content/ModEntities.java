package com.cyberday1.neoorigins.content;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, NeoOrigins.MOD_ID);

    private static final ResourceKey<EntityType<?>> COBWEB_PROJECTILE_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "cobweb_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<CobwebProjectileEntity>> COBWEB_PROJECTILE =
        ENTITY_TYPES.register("cobweb_projectile", () ->
            EntityType.Builder.<CobwebProjectileEntity>of(CobwebProjectileEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(COBWEB_PROJECTILE_KEY));

    private static final ResourceKey<EntityType<?>> HOMING_PROJECTILE_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "homing_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<HomingProjectile>> HOMING_PROJECTILE =
        ENTITY_TYPES.register("homing_projectile", () ->
            EntityType.Builder.<HomingProjectile>of(HomingProjectile::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(HOMING_PROJECTILE_KEY));

    private static final ResourceKey<EntityType<?>> MAGIC_ORB_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "magic_orb"));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicOrbProjectile>> MAGIC_ORB =
        ENTITY_TYPES.register("magic_orb", () ->
            EntityType.Builder.<MagicOrbProjectile>of(MagicOrbProjectile::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(MAGIC_ORB_KEY));

    private static final ResourceKey<EntityType<?>> LINGERING_AREA_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "lingering_area"));

    public static final DeferredHolder<EntityType<?>, EntityType<LingeringAreaEntity>> LINGERING_AREA =
        ENTITY_TYPES.register("lingering_area", () ->
            EntityType.Builder.<LingeringAreaEntity>of(LingeringAreaEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(8)
                .updateInterval(20)
                .build(LINGERING_AREA_KEY));

    private static final ResourceKey<EntityType<?>> BLACK_HOLE_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "black_hole"));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackHoleVfxEntity>> BLACK_HOLE =
        ENTITY_TYPES.register("black_hole", () ->
            EntityType.Builder.<BlackHoleVfxEntity>of(BlackHoleVfxEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(10)
                .build(BLACK_HOLE_KEY));

    private static final ResourceKey<EntityType<?>> TORNADO_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, "tornado"));

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoVfxEntity>> TORNADO =
        ENTITY_TYPES.register("tornado", () ->
            EntityType.Builder.<TornadoVfxEntity>of(TornadoVfxEntity::new, MobCategory.MISC)
                .sized(1.0F, 4.0F)
                .clientTrackingRange(16)
                .updateInterval(10)
                .build(TORNADO_KEY));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
