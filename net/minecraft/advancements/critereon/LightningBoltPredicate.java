package net.minecraft.advancements.critereon;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

public record LightningBoltPredicate(MinMaxBounds.Ints blocksSetOnFire, Optional<EntityPredicate> entityStruck) implements EntitySubPredicate {
    public static final MapCodec<LightningBoltPredicate> CODEC = RecordCodecBuilder.mapCodec(
        p_337377_ -> p_337377_.group(
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("blocks_set_on_fire", MinMaxBounds.Ints.ANY).forGetter(LightningBoltPredicate::blocksSetOnFire),
                    EntityPredicate.CODEC.optionalFieldOf("entity_struck").forGetter(LightningBoltPredicate::entityStruck)
                )
                .apply(p_337377_, LightningBoltPredicate::new)
    );

    public static LightningBoltPredicate blockSetOnFire(MinMaxBounds.Ints p_299013_) {
        return new LightningBoltPredicate(p_299013_, Optional.empty());
    }

    @Override
    public MapCodec<LightningBoltPredicate> codec() {
        return EntitySubPredicates.LIGHTNING;
    }

    @Override
    public boolean matches(Entity p_299034_, ServerLevel p_299101_, @Nullable Vec3 p_298929_) {
        return !(p_299034_ instanceof LightningBolt lightningbolt)
            ? false
            : this.blocksSetOnFire.matches(lightningbolt.getBlocksSetOnFire())
                && (
                    this.entityStruck.isEmpty()
                        || lightningbolt.getHitEntities().anyMatch(p_298360_ -> this.entityStruck.get().matches(p_299101_, p_298929_, p_298360_))
                );
    }
}
