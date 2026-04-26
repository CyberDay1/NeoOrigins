package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

record RandomGroup(SimpleWeightedRandomList<List<PoolAliasBinding>> groups) implements PoolAliasBinding {
    static MapCodec<RandomGroup> CODEC = RecordCodecBuilder.mapCodec(
        p_307566_ -> p_307566_.group(SimpleWeightedRandomList.wrappedCodec(Codec.list(PoolAliasBinding.CODEC)).fieldOf("groups").forGetter(RandomGroup::groups))
                .apply(p_307566_, RandomGroup::new)
    );

    @Override
    public void forEachResolved(RandomSource p_307472_, BiConsumer<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> p_307324_) {
        this.groups.getRandom(p_307472_).ifPresent(p_338108_ -> p_338108_.data().forEach(p_307493_ -> p_307493_.forEachResolved(p_307472_, p_307324_)));
    }

    @Override
    public Stream<ResourceKey<StructureTemplatePool>> allTargets() {
        return this.groups.unwrap().stream().flatMap(p_338109_ -> p_338109_.data().stream()).flatMap(PoolAliasBinding::allTargets);
    }

    @Override
    public MapCodec<RandomGroup> codec() {
        return CODEC;
    }
}
