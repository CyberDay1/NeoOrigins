package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record Tool(List<Tool.Rule> rules, float defaultMiningSpeed, int damagePerBlock) {
    public static final Codec<Tool> CODEC = RecordCodecBuilder.create(
        p_337953_ -> p_337953_.group(
                    Tool.Rule.CODEC.listOf().fieldOf("rules").forGetter(Tool::rules),
                    Codec.FLOAT.optionalFieldOf("default_mining_speed", Float.valueOf(1.0F)).forGetter(Tool::defaultMiningSpeed),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("damage_per_block", 1).forGetter(Tool::damagePerBlock)
                )
                .apply(p_337953_, Tool::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Tool> STREAM_CODEC = StreamCodec.composite(
        Tool.Rule.STREAM_CODEC.apply(ByteBufCodecs.list()),
        Tool::rules,
        ByteBufCodecs.FLOAT,
        Tool::defaultMiningSpeed,
        ByteBufCodecs.VAR_INT,
        Tool::damagePerBlock,
        Tool::new
    );

    public float getMiningSpeed(BlockState p_336131_) {
        for (Tool.Rule tool$rule : this.rules) {
            if (tool$rule.speed.isPresent() && p_336131_.is(tool$rule.blocks)) {
                return tool$rule.speed.get();
            }
        }

        return this.defaultMiningSpeed;
    }

    public boolean isCorrectForDrops(BlockState p_336189_) {
        for (Tool.Rule tool$rule : this.rules) {
            if (tool$rule.correctForDrops.isPresent() && p_336189_.is(tool$rule.blocks)) {
                return tool$rule.correctForDrops.get();
            }
        }

        return false;
    }

    public static record Rule(HolderSet<Block> blocks, Optional<Float> speed, Optional<Boolean> correctForDrops) {
        public static final Codec<Tool.Rule> CODEC = RecordCodecBuilder.create(
            p_337954_ -> p_337954_.group(
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(Tool.Rule::blocks),
                        ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("speed").forGetter(Tool.Rule::speed),
                        Codec.BOOL.optionalFieldOf("correct_for_drops").forGetter(Tool.Rule::correctForDrops)
                    )
                    .apply(p_337954_, Tool.Rule::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.BLOCK),
            Tool.Rule::blocks,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional),
            Tool.Rule::speed,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs::optional),
            Tool.Rule::correctForDrops,
            Tool.Rule::new
        );

        public static Tool.Rule minesAndDrops(List<Block> p_335413_, float p_335923_) {
            return forBlocks(p_335413_, Optional.of(p_335923_), Optional.of(true));
        }

        public static Tool.Rule minesAndDrops(TagKey<Block> p_335441_, float p_336060_) {
            return forTag(p_335441_, Optional.of(p_336060_), Optional.of(true));
        }

        public static Tool.Rule deniesDrops(TagKey<Block> p_335654_) {
            return forTag(p_335654_, Optional.empty(), Optional.of(false));
        }

        public static Tool.Rule overrideSpeed(TagKey<Block> p_335580_, float p_335857_) {
            return forTag(p_335580_, Optional.of(p_335857_), Optional.empty());
        }

        public static Tool.Rule overrideSpeed(List<Block> p_335776_, float p_335593_) {
            return forBlocks(p_335776_, Optional.of(p_335593_), Optional.empty());
        }

        private static Tool.Rule forTag(TagKey<Block> p_336036_, Optional<Float> p_335728_, Optional<Boolean> p_335781_) {
            return new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(p_336036_), p_335728_, p_335781_);
        }

        private static Tool.Rule forBlocks(List<Block> p_335983_, Optional<Float> p_335694_, Optional<Boolean> p_335468_) {
            return new Tool.Rule(HolderSet.direct(p_335983_.stream().map(Block::builtInRegistryHolder).collect(Collectors.toList())), p_335694_, p_335468_);
        }
    }
}
