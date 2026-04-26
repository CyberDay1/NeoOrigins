package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class PoolAliasBindings {
    public static MapCodec<? extends PoolAliasBinding> bootstrap(Registry<MapCodec<? extends PoolAliasBinding>> p_307584_) {
        Registry.register(p_307584_, "random", Random.CODEC);
        Registry.register(p_307584_, "random_group", RandomGroup.CODEC);
        return Registry.register(p_307584_, "direct", Direct.CODEC);
    }

    public static void registerTargetsAsPools(
        BootstrapContext<StructureTemplatePool> p_321695_, Holder<StructureTemplatePool> p_312865_, List<PoolAliasBinding> p_311809_
    ) {
        p_311809_.stream()
            .flatMap(PoolAliasBinding::allTargets)
            .map(p_312156_ -> p_312156_.location().getPath())
            .forEach(
                p_321475_ -> Pools.register(
                        p_321695_,
                        p_321475_,
                        new StructureTemplatePool(
                            p_312865_, List.of(Pair.of(StructurePoolElement.single(p_321475_), 1)), StructureTemplatePool.Projection.RIGID
                        )
                    )
            );
    }
}
