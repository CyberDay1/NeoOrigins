package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = p_307289_ -> p_307289_;

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> p_307512_);

    static PoolAliasLookup create(List<PoolAliasBinding> p_307423_, BlockPos p_307208_, long p_307622_) {
        if (p_307423_.isEmpty()) {
            return EMPTY;
        } else {
            RandomSource randomsource = RandomSource.create(p_307622_).forkPositional().at(p_307208_);
            Builder<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> builder = ImmutableMap.builder();
            p_307423_.forEach(p_307533_ -> p_307533_.forEachResolved(randomsource, builder::put));
            Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = builder.build();
            return p_307442_ -> Objects.requireNonNull(map.getOrDefault(p_307442_, p_307442_), () -> "alias " + p_307442_ + " was mapped to null value");
        }
    }
}
