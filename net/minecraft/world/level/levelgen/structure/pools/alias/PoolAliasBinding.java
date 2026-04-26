package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public interface PoolAliasBinding {
    Codec<PoolAliasBinding> CODEC = BuiltInRegistries.POOL_ALIAS_BINDING_TYPE.byNameCodec().dispatch(PoolAliasBinding::codec, Function.identity());

    void forEachResolved(RandomSource p_307322_, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> p_307269_);

    Stream<ResourceKey<StructureTemplatePool>> allTargets();

    static Direct direct(String p_307227_, String p_307334_) {
        return direct(Pools.createKey(p_307227_), Pools.createKey(p_307334_));
    }

    static Direct direct(ResourceKey<StructureTemplatePool> p_307379_, ResourceKey<StructureTemplatePool> p_307242_) {
        return new Direct(p_307379_, p_307242_);
    }

    static Random random(String p_307435_, SimpleWeightedRandomList<String> p_307653_) {
        SimpleWeightedRandomList.Builder<ResourceKey<StructureTemplatePool>> builder = SimpleWeightedRandomList.builder();
        p_307653_.unwrap().forEach(p_338103_ -> builder.add(Pools.createKey(p_338103_.data()), p_338103_.getWeight().asInt()));
        return random(Pools.createKey(p_307435_), builder.build());
    }

    static Random random(ResourceKey<StructureTemplatePool> p_307260_, SimpleWeightedRandomList<ResourceKey<StructureTemplatePool>> p_307665_) {
        return new Random(p_307260_, p_307665_);
    }

    static RandomGroup randomGroup(SimpleWeightedRandomList<List<PoolAliasBinding>> p_307498_) {
        return new RandomGroup(p_307498_);
    }

    MapCodec<? extends PoolAliasBinding> codec();
}
