package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public record BlockPredicate(Optional<HolderSet<Block>> blocks, Optional<StatePropertiesPredicate> properties, Optional<NbtPredicate> nbt) {
    public static final Codec<BlockPredicate> CODEC = RecordCodecBuilder.create(
        p_337342_ -> p_337342_.group(
                    RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("blocks").forGetter(BlockPredicate::blocks),
                    StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(BlockPredicate::properties),
                    NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(BlockPredicate::nbt)
                )
                .apply(p_337342_, BlockPredicate::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPredicate> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(ByteBufCodecs.holderSet(Registries.BLOCK)),
        BlockPredicate::blocks,
        ByteBufCodecs.optional(StatePropertiesPredicate.STREAM_CODEC),
        BlockPredicate::properties,
        ByteBufCodecs.optional(NbtPredicate.STREAM_CODEC),
        BlockPredicate::nbt,
        BlockPredicate::new
    );

    public boolean matches(ServerLevel p_17915_, BlockPos p_17916_) {
        if (!p_17915_.isLoaded(p_17916_)) {
            return false;
        } else {
            return !this.matchesState(p_17915_.getBlockState(p_17916_))
                ? false
                : !this.nbt.isPresent() || matchesBlockEntity(p_17915_, p_17915_.getBlockEntity(p_17916_), this.nbt.get());
        }
    }

    public boolean matches(BlockInWorld p_330448_) {
        return !this.matchesState(p_330448_.getState())
            ? false
            : !this.nbt.isPresent() || matchesBlockEntity(p_330448_.getLevel(), p_330448_.getEntity(), this.nbt.get());
    }

    private boolean matchesState(BlockState p_330349_) {
        return this.blocks.isPresent() && !p_330349_.is(this.blocks.get()) ? false : !this.properties.isPresent() || this.properties.get().matches(p_330349_);
    }

    private static boolean matchesBlockEntity(LevelReader p_330457_, @Nullable BlockEntity p_332206_, NbtPredicate p_330422_) {
        return p_332206_ != null && p_330422_.matches(p_332206_.saveWithFullMetadata(p_330457_.registryAccess()));
    }

    public boolean requiresNbt() {
        return this.nbt.isPresent();
    }

    public static class Builder {
        private Optional<HolderSet<Block>> blocks = Optional.empty();
        private Optional<StatePropertiesPredicate> properties = Optional.empty();
        private Optional<NbtPredicate> nbt = Optional.empty();

        private Builder() {
        }

        public static BlockPredicate.Builder block() {
            return new BlockPredicate.Builder();
        }

        public BlockPredicate.Builder of(Block... p_146727_) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, p_146727_));
            return this;
        }

        public BlockPredicate.Builder of(Collection<Block> p_298407_) {
            this.blocks = Optional.of(HolderSet.direct(Block::builtInRegistryHolder, p_298407_));
            return this;
        }

        public BlockPredicate.Builder of(TagKey<Block> p_204028_) {
            this.blocks = Optional.of(BuiltInRegistries.BLOCK.getOrCreateTag(p_204028_));
            return this;
        }

        public BlockPredicate.Builder hasNbt(CompoundTag p_146725_) {
            this.nbt = Optional.of(new NbtPredicate(p_146725_));
            return this;
        }

        public BlockPredicate.Builder setProperties(StatePropertiesPredicate.Builder p_298989_) {
            this.properties = p_298989_.build();
            return this;
        }

        public BlockPredicate build() {
            return new BlockPredicate(this.blocks, this.properties, this.nbt);
        }
    }
}
