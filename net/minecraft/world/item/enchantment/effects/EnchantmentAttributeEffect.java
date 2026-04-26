package net.minecraft.world.item.enchantment.effects;

import com.google.common.collect.HashMultimap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record EnchantmentAttributeEffect(ResourceLocation id, Holder<Attribute> attribute, LevelBasedValue amount, AttributeModifier.Operation operation)
    implements EnchantmentLocationBasedEffect {
    public static final MapCodec<EnchantmentAttributeEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_350198_ -> p_350198_.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(EnchantmentAttributeEffect::id),
                    Attribute.CODEC.fieldOf("attribute").forGetter(EnchantmentAttributeEffect::attribute),
                    LevelBasedValue.CODEC.fieldOf("amount").forGetter(EnchantmentAttributeEffect::amount),
                    AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(EnchantmentAttributeEffect::operation)
                )
                .apply(p_350198_, EnchantmentAttributeEffect::new)
    );

    private ResourceLocation idForSlot(StringRepresentable p_350940_) {
        return this.id.withSuffix("/" + p_350940_.getSerializedName());
    }

    public AttributeModifier getModifier(int p_345225_, StringRepresentable p_350734_) {
        return new AttributeModifier(this.idForSlot(p_350734_), (double)this.amount().calculate(p_345225_), this.operation());
    }

    @Override
    public void onChangedBlock(ServerLevel p_346176_, int p_345071_, EnchantedItemInUse p_345394_, Entity p_345539_, Vec3 p_346261_, boolean p_345801_) {
        if (p_345801_ && p_345539_ instanceof LivingEntity livingentity) {
            livingentity.getAttributes().addTransientAttributeModifiers(this.makeAttributeMap(p_345071_, p_345394_.inSlot()));
        }
    }

    @Override
    public void onDeactivated(EnchantedItemInUse p_346016_, Entity p_346371_, Vec3 p_345145_, int p_346185_) {
        if (p_346371_ instanceof LivingEntity livingentity) {
            livingentity.getAttributes().removeAttributeModifiers(this.makeAttributeMap(p_346185_, p_346016_.inSlot()));
        }
    }

    private HashMultimap<Holder<Attribute>, AttributeModifier> makeAttributeMap(int p_346033_, EquipmentSlot p_350290_) {
        HashMultimap<Holder<Attribute>, AttributeModifier> hashmultimap = HashMultimap.create();
        hashmultimap.put(this.attribute, this.getModifier(p_346033_, p_350290_));
        return hashmultimap;
    }

    @Override
    public MapCodec<EnchantmentAttributeEffect> codec() {
        return CODEC;
    }
}
