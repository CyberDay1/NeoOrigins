package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetAttributesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetAttributesFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_347439_ -> commonFields(p_347439_)
                .and(
                    p_347439_.group(
                        SetAttributesFunction.Modifier.CODEC.listOf().fieldOf("modifiers").forGetter(p_298099_ -> p_298099_.modifiers),
                        Codec.BOOL.optionalFieldOf("replace", Boolean.valueOf(true)).forGetter(p_339576_ -> p_339576_.replace)
                    )
                )
                .apply(p_347439_, SetAttributesFunction::new)
    );
    private final List<SetAttributesFunction.Modifier> modifiers;
    private final boolean replace;

    SetAttributesFunction(List<LootItemCondition> p_80834_, List<SetAttributesFunction.Modifier> p_298646_, boolean p_339689_) {
        super(p_80834_);
        this.modifiers = List.copyOf(p_298646_);
        this.replace = p_339689_;
    }

    @Override
    public LootItemFunctionType<SetAttributesFunction> getType() {
        return LootItemFunctions.SET_ATTRIBUTES;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.modifiers.stream().flatMap(p_279080_ -> p_279080_.amount.getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack p_80840_, LootContext p_80841_) {
        if (this.replace) {
            p_80840_.set(DataComponents.ATTRIBUTE_MODIFIERS, this.updateModifiers(p_80841_, ItemAttributeModifiers.EMPTY));
        } else {
            p_80840_.update(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY,
                p_339574_ -> p_339574_.modifiers().isEmpty()
                        ? this.updateModifiers(p_80841_, p_80840_.getItem().getDefaultAttributeModifiers())
                        : this.updateModifiers(p_80841_, p_339574_)
            );
        }

        return p_80840_;
    }

    private ItemAttributeModifiers updateModifiers(LootContext p_339627_, ItemAttributeModifiers p_339590_) {
        RandomSource randomsource = p_339627_.getRandom();

        for (SetAttributesFunction.Modifier setattributesfunction$modifier : this.modifiers) {
            EquipmentSlotGroup equipmentslotgroup = Util.getRandom(setattributesfunction$modifier.slots, randomsource);
            p_339590_ = p_339590_.withModifierAdded(
                setattributesfunction$modifier.attribute,
                new AttributeModifier(
                    setattributesfunction$modifier.id,
                    (double)setattributesfunction$modifier.amount.getFloat(p_339627_),
                    setattributesfunction$modifier.operation
                ),
                equipmentslotgroup
            );
        }

        return p_339590_;
    }

    public static SetAttributesFunction.ModifierBuilder modifier(
        ResourceLocation p_350454_, Holder<Attribute> p_298306_, AttributeModifier.Operation p_165238_, NumberProvider p_165239_
    ) {
        return new SetAttributesFunction.ModifierBuilder(p_350454_, p_298306_, p_165238_, p_165239_);
    }

    public static SetAttributesFunction.Builder setAttributes() {
        return new SetAttributesFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetAttributesFunction.Builder> {
        private final boolean replace;
        private final List<SetAttributesFunction.Modifier> modifiers = Lists.newArrayList();

        public Builder(boolean p_339611_) {
            this.replace = p_339611_;
        }

        public Builder() {
            this(false);
        }

        protected SetAttributesFunction.Builder getThis() {
            return this;
        }

        public SetAttributesFunction.Builder withModifier(SetAttributesFunction.ModifierBuilder p_165246_) {
            this.modifiers.add(p_165246_.build());
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetAttributesFunction(this.getConditions(), this.modifiers, this.replace);
        }
    }

    static record Modifier(
        ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount, List<EquipmentSlotGroup> slots
    ) {
        private static final Codec<List<EquipmentSlotGroup>> SLOTS_CODEC = ExtraCodecs.nonEmptyList(
            Codec.either(EquipmentSlotGroup.CODEC, EquipmentSlotGroup.CODEC.listOf())
                .xmap(
                    p_298227_ -> p_298227_.map(List::of, Function.identity()),
                    p_339577_ -> p_339577_.size() == 1 ? Either.left(p_339577_.getFirst()) : Either.right((List<EquipmentSlotGroup>)p_339577_)
                )
        );
        public static final Codec<SetAttributesFunction.Modifier> CODEC = RecordCodecBuilder.create(
            p_350260_ -> p_350260_.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(SetAttributesFunction.Modifier::id),
                        Attribute.CODEC.fieldOf("attribute").forGetter(SetAttributesFunction.Modifier::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(SetAttributesFunction.Modifier::operation),
                        NumberProviders.CODEC.fieldOf("amount").forGetter(SetAttributesFunction.Modifier::amount),
                        SLOTS_CODEC.fieldOf("slot").forGetter(SetAttributesFunction.Modifier::slots)
                    )
                    .apply(p_350260_, SetAttributesFunction.Modifier::new)
        );
    }

    public static class ModifierBuilder {
        private final ResourceLocation id;
        private final Holder<Attribute> attribute;
        private final AttributeModifier.Operation operation;
        private final NumberProvider amount;
        private final Set<EquipmentSlotGroup> slots = EnumSet.noneOf(EquipmentSlotGroup.class);

        public ModifierBuilder(ResourceLocation p_350927_, Holder<Attribute> p_298853_, AttributeModifier.Operation p_165265_, NumberProvider p_165266_) {
            this.id = p_350927_;
            this.attribute = p_298853_;
            this.operation = p_165265_;
            this.amount = p_165266_;
        }

        public SetAttributesFunction.ModifierBuilder forSlot(EquipmentSlotGroup p_332133_) {
            this.slots.add(p_332133_);
            return this;
        }

        public SetAttributesFunction.Modifier build() {
            return new SetAttributesFunction.Modifier(this.id, this.attribute, this.operation, this.amount, List.copyOf(this.slots));
        }
    }
}
