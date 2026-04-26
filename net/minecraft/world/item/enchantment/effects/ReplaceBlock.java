package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceBlock(Vec3i offset, Optional<BlockPredicate> predicate, BlockStateProvider blockState, Optional<Holder<GameEvent>> triggerGameEvent)
    implements EnchantmentEntityEffect {
    public static final MapCodec<ReplaceBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_347355_ -> p_347355_.group(
                    Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceBlock::offset),
                    BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceBlock::predicate),
                    BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceBlock::blockState),
                    GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceBlock::triggerGameEvent)
                )
                .apply(p_347355_, ReplaceBlock::new)
    );

    @Override
    public void apply(ServerLevel p_345756_, int p_346215_, EnchantedItemInUse p_345202_, Entity p_346293_, Vec3 p_345339_) {
        BlockPos blockpos = BlockPos.containing(p_345339_).offset(this.offset);
        if (this.predicate.map(p_345193_ -> p_345193_.test(p_345756_, blockpos)).orElse(true)
            && p_345756_.setBlockAndUpdate(blockpos, this.blockState.getState(p_346293_.getRandom(), blockpos))) {
            this.triggerGameEvent.ifPresent(p_347354_ -> p_345756_.gameEvent(p_346293_, (Holder<GameEvent>)p_347354_, blockpos));
        }
    }

    @Override
    public MapCodec<ReplaceBlock> codec() {
        return CODEC;
    }
}
