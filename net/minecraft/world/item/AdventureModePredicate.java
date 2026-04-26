package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

public class AdventureModePredicate {
    private static final Codec<AdventureModePredicate> SIMPLE_CODEC = BlockPredicate.CODEC
        .flatComapMap(p_330742_ -> new AdventureModePredicate(List.of(p_330742_), true), p_330884_ -> DataResult.error(() -> "Cannot encode"));
    private static final Codec<AdventureModePredicate> FULL_CODEC = RecordCodecBuilder.create(
        p_337904_ -> p_337904_.group(
                    ExtraCodecs.nonEmptyList(BlockPredicate.CODEC.listOf()).fieldOf("predicates").forGetter(p_331329_ -> p_331329_.predicates),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(AdventureModePredicate::showInTooltip)
                )
                .apply(p_337904_, AdventureModePredicate::new)
    );
    public static final Codec<AdventureModePredicate> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdventureModePredicate> STREAM_CODEC = StreamCodec.composite(
        BlockPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
        p_331439_ -> p_331439_.predicates,
        ByteBufCodecs.BOOL,
        AdventureModePredicate::showInTooltip,
        AdventureModePredicate::new
    );
    public static final Component CAN_BREAK_HEADER = Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY);
    public static final Component CAN_PLACE_HEADER = Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY);
    private static final Component UNKNOWN_USE = Component.translatable("item.canUse.unknown").withStyle(ChatFormatting.GRAY);
    private final List<BlockPredicate> predicates;
    private final boolean showInTooltip;
    private final List<Component> tooltip;
    @Nullable
    private BlockInWorld lastCheckedBlock;
    private boolean lastResult;
    private boolean checksBlockEntity;

    private AdventureModePredicate(List<BlockPredicate> p_335644_, boolean p_335774_, List<Component> p_336056_) {
        this.predicates = p_335644_;
        this.showInTooltip = p_335774_;
        this.tooltip = p_336056_;
    }

    public AdventureModePredicate(List<BlockPredicate> p_331906_, boolean p_331780_) {
        this.predicates = p_331906_;
        this.showInTooltip = p_331780_;
        this.tooltip = computeTooltip(p_331906_);
    }

    private static boolean areSameBlocks(BlockInWorld p_330901_, @Nullable BlockInWorld p_331435_, boolean p_332152_) {
        if (p_331435_ == null || p_330901_.getState() != p_331435_.getState()) {
            return false;
        } else if (!p_332152_) {
            return true;
        } else if (p_330901_.getEntity() == null && p_331435_.getEntity() == null) {
            return true;
        } else if (p_330901_.getEntity() != null && p_331435_.getEntity() != null) {
            RegistryAccess registryaccess = p_330901_.getLevel().registryAccess();
            return Objects.equals(p_330901_.getEntity().saveWithId(registryaccess), p_331435_.getEntity().saveWithId(registryaccess));
        } else {
            return false;
        }
    }

    public boolean test(BlockInWorld p_331911_) {
        if (areSameBlocks(p_331911_, this.lastCheckedBlock, this.checksBlockEntity)) {
            return this.lastResult;
        } else {
            this.lastCheckedBlock = p_331911_;
            this.checksBlockEntity = false;

            for (BlockPredicate blockpredicate : this.predicates) {
                if (blockpredicate.matches(p_331911_)) {
                    this.checksBlockEntity = this.checksBlockEntity | blockpredicate.requiresNbt();
                    this.lastResult = true;
                    return true;
                }
            }

            this.lastResult = false;
            return false;
        }
    }

    public void addToTooltip(Consumer<Component> p_331792_) {
        this.tooltip.forEach(p_331792_);
    }

    public AdventureModePredicate withTooltip(boolean p_335684_) {
        return new AdventureModePredicate(this.predicates, p_335684_, this.tooltip);
    }

    private static List<Component> computeTooltip(List<BlockPredicate> p_332164_) {
        for (BlockPredicate blockpredicate : p_332164_) {
            if (blockpredicate.blocks().isEmpty()) {
                return List.of(UNKNOWN_USE);
            }
        }

        return p_332164_.stream()
            .flatMap(p_330395_ -> p_330395_.blocks().orElseThrow().stream())
            .distinct()
            .map(p_331662_ -> (Component)p_331662_.value().getName().withStyle(ChatFormatting.DARK_GRAY))
            .toList();
    }

    public boolean showInTooltip() {
        return this.showInTooltip;
    }

    @Override
    public boolean equals(Object p_330844_) {
        if (this == p_330844_) {
            return true;
        } else {
            return !(p_330844_ instanceof AdventureModePredicate adventuremodepredicate)
                ? false
                : this.predicates.equals(adventuremodepredicate.predicates) && this.showInTooltip == adventuremodepredicate.showInTooltip;
        }
    }

    @Override
    public int hashCode() {
        return this.predicates.hashCode() * 31 + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "AdventureModePredicate{predicates=" + this.predicates + ", showInTooltip=" + this.showInTooltip + "}";
    }
}
