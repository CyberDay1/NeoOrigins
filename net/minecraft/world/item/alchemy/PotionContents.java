package net.minecraft.world.item.alchemy;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor, List<MobEffectInstance> customEffects) {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of());
    private static final Component NO_EFFECT = Component.translatable("effect.none").withStyle(ChatFormatting.GRAY);
    private static final int BASE_POTION_COLOR = -13083194;
    private static final Codec<PotionContents> FULL_CODEC = RecordCodecBuilder.create(
        p_348387_ -> p_348387_.group(
                    Potion.CODEC.optionalFieldOf("potion").forGetter(PotionContents::potion),
                    Codec.INT.optionalFieldOf("custom_color").forGetter(PotionContents::customColor),
                    MobEffectInstance.CODEC.listOf().optionalFieldOf("custom_effects", List.of()).forGetter(PotionContents::customEffects)
                )
                .apply(p_348387_, PotionContents::new)
    );
    public static final Codec<PotionContents> CODEC = Codec.withAlternative(FULL_CODEC, Potion.CODEC, PotionContents::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionContents> STREAM_CODEC = StreamCodec.composite(
        Potion.STREAM_CODEC.apply(ByteBufCodecs::optional),
        PotionContents::potion,
        ByteBufCodecs.INT.apply(ByteBufCodecs::optional),
        PotionContents::customColor,
        MobEffectInstance.STREAM_CODEC.apply(ByteBufCodecs.list()),
        PotionContents::customEffects,
        PotionContents::new
    );

    public PotionContents(Holder<Potion> p_331208_) {
        this(Optional.of(p_331208_), Optional.empty(), List.of());
    }

    public static ItemStack createItemStack(Item p_330388_, Holder<Potion> p_331030_) {
        ItemStack itemstack = new ItemStack(p_330388_);
        itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(p_331030_));
        return itemstack;
    }

    public boolean is(Holder<Potion> p_331328_) {
        return this.potion.isPresent() && this.potion.get().is(p_331328_) && this.customEffects.isEmpty();
    }

    public Iterable<MobEffectInstance> getAllEffects() {
        if (this.potion.isEmpty()) {
            return this.customEffects;
        } else {
            return (Iterable<MobEffectInstance>)(this.customEffects.isEmpty()
                ? this.potion.get().value().getEffects()
                : Iterables.concat(this.potion.get().value().getEffects(), this.customEffects));
        }
    }

    public void forEachEffect(Consumer<MobEffectInstance> p_331190_) {
        if (this.potion.isPresent()) {
            for (MobEffectInstance mobeffectinstance : this.potion.get().value().getEffects()) {
                p_331190_.accept(new MobEffectInstance(mobeffectinstance));
            }
        }

        for (MobEffectInstance mobeffectinstance1 : this.customEffects) {
            p_331190_.accept(new MobEffectInstance(mobeffectinstance1));
        }
    }

    public PotionContents withPotion(Holder<Potion> p_330288_) {
        return new PotionContents(Optional.of(p_330288_), this.customColor, this.customEffects);
    }

    public PotionContents withEffectAdded(MobEffectInstance p_332083_) {
        return new PotionContents(this.potion, this.customColor, Util.copyAndAdd(this.customEffects, p_332083_));
    }

    public int getColor() {
        return this.customColor.isPresent() ? this.customColor.get() : getColor(this.getAllEffects());
    }

    public static int getColor(Holder<Potion> p_330619_) {
        return getColor(p_330619_.value().getEffects());
    }

    public static int getColor(Iterable<MobEffectInstance> p_331934_) {
        return getColorOptional(p_331934_).orElse(-13083194);
    }

    public static OptionalInt getColorOptional(Iterable<MobEffectInstance> p_332699_) {
        int i = 0;
        int j = 0;
        int k = 0;
        int l = 0;

        for (MobEffectInstance mobeffectinstance : p_332699_) {
            if (mobeffectinstance.isVisible()) {
                int i1 = mobeffectinstance.getEffect().value().getColor();
                int j1 = mobeffectinstance.getAmplifier() + 1;
                i += j1 * FastColor.ARGB32.red(i1);
                j += j1 * FastColor.ARGB32.green(i1);
                k += j1 * FastColor.ARGB32.blue(i1);
                l += j1;
            }
        }

        return l == 0 ? OptionalInt.empty() : OptionalInt.of(FastColor.ARGB32.color(i / l, j / l, k / l));
    }

    public boolean hasEffects() {
        return !this.customEffects.isEmpty() ? true : this.potion.isPresent() && !this.potion.get().value().getEffects().isEmpty();
    }

    public List<MobEffectInstance> customEffects() {
        return Lists.transform(this.customEffects, MobEffectInstance::new);
    }

    public void addPotionTooltip(Consumer<Component> p_331688_, float p_330466_, float p_332190_) {
        addPotionTooltip(this.getAllEffects(), p_331688_, p_330466_, p_332190_);
    }

    public static void addPotionTooltip(Iterable<MobEffectInstance> p_331825_, Consumer<Component> p_331296_, float p_332038_, float p_332014_) {
        List<Pair<Holder<Attribute>, AttributeModifier>> list = Lists.newArrayList();
        boolean flag = true;

        for (MobEffectInstance mobeffectinstance : p_331825_) {
            flag = false;
            MutableComponent mutablecomponent = Component.translatable(mobeffectinstance.getDescriptionId());
            Holder<MobEffect> holder = mobeffectinstance.getEffect();
            holder.value().createModifiers(mobeffectinstance.getAmplifier(), (p_331556_, p_330860_) -> list.add(new Pair<>(p_331556_, p_330860_)));
            if (mobeffectinstance.getAmplifier() > 0) {
                mutablecomponent = Component.translatable(
                    "potion.withAmplifier", mutablecomponent, Component.translatable("potion.potency." + mobeffectinstance.getAmplifier())
                );
            }

            if (!mobeffectinstance.endsWithin(20)) {
                mutablecomponent = Component.translatable(
                    "potion.withDuration", mutablecomponent, MobEffectUtil.formatDuration(mobeffectinstance, p_332038_, p_332014_)
                );
            }

            p_331296_.accept(mutablecomponent.withStyle(holder.value().getCategory().getTooltipFormatting()));
        }

        if (flag) {
            p_331296_.accept(NO_EFFECT);
        }

        if (!list.isEmpty()) {
            p_331296_.accept(CommonComponents.EMPTY);
            p_331296_.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

            // Neo: Override handling of potion attribute tooltips to support IAttributeExtension
            net.neoforged.neoforge.common.util.AttributeUtil.addPotionTooltip(list, p_331296_);
            if (true) return;

            for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
                AttributeModifier attributemodifier = pair.getSecond();
                double d1 = attributemodifier.amount();
                double d0;
                if (attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    && attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    d0 = attributemodifier.amount();
                } else {
                    d0 = attributemodifier.amount() * 100.0;
                }

                if (d1 > 0.0) {
                    p_331296_.accept(
                        Component.translatable(
                                "attribute.modifier.plus." + attributemodifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d0),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.BLUE)
                    );
                } else if (d1 < 0.0) {
                    d0 *= -1.0;
                    p_331296_.accept(
                        Component.translatable(
                                "attribute.modifier.take." + attributemodifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d0),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.RED)
                    );
                }
            }
        }
    }
}
