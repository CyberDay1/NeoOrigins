package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProvider;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProviders;
import org.apache.commons.lang3.mutable.MutableObject;

public class CopyCustomDataFunction extends LootItemConditionalFunction {
    public static final MapCodec<CopyCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_330353_ -> commonFields(p_330353_)
                .and(
                    p_330353_.group(
                        NbtProviders.CODEC.fieldOf("source").forGetter(p_331496_ -> p_331496_.source),
                        CopyCustomDataFunction.CopyOperation.CODEC.listOf().fieldOf("ops").forGetter(p_330474_ -> p_330474_.operations)
                    )
                )
                .apply(p_330353_, CopyCustomDataFunction::new)
    );
    private final NbtProvider source;
    private final List<CopyCustomDataFunction.CopyOperation> operations;

    CopyCustomDataFunction(List<LootItemCondition> p_330826_, NbtProvider p_331866_, List<CopyCustomDataFunction.CopyOperation> p_332090_) {
        super(p_330826_);
        this.source = p_331866_;
        this.operations = List.copyOf(p_332090_);
    }

    @Override
    public LootItemFunctionType<CopyCustomDataFunction> getType() {
        return LootItemFunctions.COPY_CUSTOM_DATA;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.source.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack p_330210_, LootContext p_330315_) {
        Tag tag = this.source.get(p_330315_);
        if (tag == null) {
            return p_330210_;
        } else {
            MutableObject<CompoundTag> mutableobject = new MutableObject<>();
            Supplier<Tag> supplier = () -> {
                if (mutableobject.getValue() == null) {
                    mutableobject.setValue(p_330210_.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
                }

                return mutableobject.getValue();
            };
            this.operations.forEach(p_330417_ -> p_330417_.apply(supplier, tag));
            CompoundTag compoundtag = mutableobject.getValue();
            if (compoundtag != null) {
                CustomData.set(DataComponents.CUSTOM_DATA, p_330210_, compoundtag);
            }

            return p_330210_;
        }
    }

    @Deprecated
    public static CopyCustomDataFunction.Builder copyData(NbtProvider p_330285_) {
        return new CopyCustomDataFunction.Builder(p_330285_);
    }

    public static CopyCustomDataFunction.Builder copyData(LootContext.EntityTarget p_330616_) {
        return new CopyCustomDataFunction.Builder(ContextNbtProvider.forContextEntity(p_330616_));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<CopyCustomDataFunction.Builder> {
        private final NbtProvider source;
        private final List<CopyCustomDataFunction.CopyOperation> ops = Lists.newArrayList();

        Builder(NbtProvider p_331441_) {
            this.source = p_331441_;
        }

        public CopyCustomDataFunction.Builder copy(String p_331086_, String p_331216_, CopyCustomDataFunction.MergeStrategy p_331878_) {
            try {
                this.ops.add(new CopyCustomDataFunction.CopyOperation(NbtPathArgument.NbtPath.of(p_331086_), NbtPathArgument.NbtPath.of(p_331216_), p_331878_));
                return this;
            } catch (CommandSyntaxException commandsyntaxexception) {
                throw new IllegalArgumentException(commandsyntaxexception);
            }
        }

        public CopyCustomDataFunction.Builder copy(String p_331730_, String p_330347_) {
            return this.copy(p_331730_, p_330347_, CopyCustomDataFunction.MergeStrategy.REPLACE);
        }

        protected CopyCustomDataFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new CopyCustomDataFunction(this.getConditions(), this.source, this.ops);
        }
    }

    static record CopyOperation(NbtPathArgument.NbtPath sourcePath, NbtPathArgument.NbtPath targetPath, CopyCustomDataFunction.MergeStrategy op) {
        public static final Codec<CopyCustomDataFunction.CopyOperation> CODEC = RecordCodecBuilder.create(
            p_335333_ -> p_335333_.group(
                        NbtPathArgument.NbtPath.CODEC.fieldOf("source").forGetter(CopyCustomDataFunction.CopyOperation::sourcePath),
                        NbtPathArgument.NbtPath.CODEC.fieldOf("target").forGetter(CopyCustomDataFunction.CopyOperation::targetPath),
                        CopyCustomDataFunction.MergeStrategy.CODEC.fieldOf("op").forGetter(CopyCustomDataFunction.CopyOperation::op)
                    )
                    .apply(p_335333_, CopyCustomDataFunction.CopyOperation::new)
        );

        public void apply(Supplier<Tag> p_330493_, Tag p_331306_) {
            try {
                List<Tag> list = this.sourcePath.get(p_331306_);
                if (!list.isEmpty()) {
                    this.op.merge(p_330493_.get(), this.targetPath, list);
                }
            } catch (CommandSyntaxException commandsyntaxexception) {
            }
        }
    }

    public static enum MergeStrategy implements StringRepresentable {
        REPLACE("replace") {
            @Override
            public void merge(Tag p_331979_, NbtPathArgument.NbtPath p_331124_, List<Tag> p_330968_) throws CommandSyntaxException {
                p_331124_.set(p_331979_, Iterables.getLast(p_330968_));
            }
        },
        APPEND("append") {
            @Override
            public void merge(Tag p_332012_, NbtPathArgument.NbtPath p_330758_, List<Tag> p_331021_) throws CommandSyntaxException {
                List<Tag> list = p_330758_.getOrCreate(p_332012_, ListTag::new);
                list.forEach(p_331722_ -> {
                    if (p_331722_ instanceof ListTag) {
                        p_331021_.forEach(p_331278_ -> ((ListTag)p_331722_).add(p_331278_.copy()));
                    }
                });
            }
        },
        MERGE("merge") {
            @Override
            public void merge(Tag p_330243_, NbtPathArgument.NbtPath p_331218_, List<Tag> p_332107_) throws CommandSyntaxException {
                List<Tag> list = p_331218_.getOrCreate(p_330243_, CompoundTag::new);
                list.forEach(p_330516_ -> {
                    if (p_330516_ instanceof CompoundTag) {
                        p_332107_.forEach(p_332126_ -> {
                            if (p_332126_ instanceof CompoundTag) {
                                ((CompoundTag)p_330516_).merge((CompoundTag)p_332126_);
                            }
                        });
                    }
                });
            }
        };

        public static final Codec<CopyCustomDataFunction.MergeStrategy> CODEC = StringRepresentable.fromEnum(CopyCustomDataFunction.MergeStrategy::values);
        private final String name;

        public abstract void merge(Tag p_330558_, NbtPathArgument.NbtPath p_331810_, List<Tag> p_330953_) throws CommandSyntaxException;

        MergeStrategy(String p_331709_) {
            this.name = p_331709_;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
