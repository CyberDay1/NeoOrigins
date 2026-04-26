package net.minecraft.data.worldgen;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class Pools {
    public static final ResourceKey<StructureTemplatePool> EMPTY = createKey("empty");

    public static ResourceKey<StructureTemplatePool> createKey(String p_256439_) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.withDefaultNamespace(p_256439_));
    }

    public static ResourceKey<StructureTemplatePool> parseKey(String p_350360_) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(p_350360_));
    }

    public static void register(BootstrapContext<StructureTemplatePool> p_321699_, String p_255837_, StructureTemplatePool p_256161_) {
        p_321699_.register(createKey(p_255837_), p_256161_);
    }

    public static void bootstrap(BootstrapContext<StructureTemplatePool> p_321867_) {
        HolderGetter<StructureTemplatePool> holdergetter = p_321867_.lookup(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> holder = holdergetter.getOrThrow(EMPTY);
        p_321867_.register(EMPTY, new StructureTemplatePool(holder, ImmutableList.of(), StructureTemplatePool.Projection.RIGID));
        BastionPieces.bootstrap(p_321867_);
        PillagerOutpostPools.bootstrap(p_321867_);
        VillagePools.bootstrap(p_321867_);
        AncientCityStructurePieces.bootstrap(p_321867_);
        TrailRuinsStructurePools.bootstrap(p_321867_);
        TrialChambersStructurePools.bootstrap(p_321867_);
    }
}
