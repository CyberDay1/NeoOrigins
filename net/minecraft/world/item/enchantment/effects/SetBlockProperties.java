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
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record SetBlockProperties(BlockItemStateProperties properties, Vec3i offset, Optional<Holder<GameEvent>> triggerGameEvent)
    implements EnchantmentEntityEffect {
    public static final MapCodec<SetBlockProperties> CODEC = RecordCodecBuilder.mapCodec(
        p_347361_ -> p_347361_.group(
                    BlockItemStateProperties.CODEC.fieldOf("properties").forGetter(SetBlockProperties::properties),
                    Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(SetBlockProperties::offset),
                    GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(SetBlockProperties::triggerGameEvent)
                )
                .apply(p_347361_, SetBlockProperties::new)
    );

    public SetBlockProperties(BlockItemStateProperties p_346404_) {
        this(p_346404_, Vec3i.ZERO, Optional.of(GameEvent.BLOCK_CHANGE));
    }

    @Override
    public void apply(ServerLevel p_346105_, int p_345373_, EnchantedItemInUse p_346028_, Entity p_346068_, Vec3 p_345511_) {
        BlockPos blockpos = BlockPos.containing(p_345511_).offset(this.offset);
        BlockState blockstate = p_346068_.level().getBlockState(blockpos);
        BlockState blockstate1 = this.properties.apply(blockstate);
        if (!blockstate.equals(blockstate1) && p_346068_.level().setBlock(blockpos, blockstate1, 3)) {
            this.triggerGameEvent.ifPresent(p_347365_ -> p_346105_.gameEvent(p_346068_, (Holder<GameEvent>)p_347365_, blockpos));
        }
    }

    @Override
    public MapCodec<SetBlockProperties> codec() {
        return CODEC;
    }
}
