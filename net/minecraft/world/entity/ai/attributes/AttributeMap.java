package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Holder<Attribute>, AttributeInstance> attributes = new Object2ObjectOpenHashMap<>();
    private final Set<AttributeInstance> attributesToSync = new ObjectOpenHashSet<>();
    private final Set<AttributeInstance> attributesToUpdate = new ObjectOpenHashSet<>();
    private final AttributeSupplier supplier;

    public AttributeMap(AttributeSupplier p_22144_) {
        this.supplier = p_22144_;
    }

    private void onAttributeModified(AttributeInstance p_22158_) {
        this.attributesToUpdate.add(p_22158_);
        if (p_22158_.getAttribute().value().isClientSyncable()) {
            this.attributesToSync.add(p_22158_);
        }
    }

    public Set<AttributeInstance> getAttributesToSync() {
        return this.attributesToSync;
    }

    public Set<AttributeInstance> getAttributesToUpdate() {
        return this.attributesToUpdate;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter(p_315935_ -> p_315935_.getAttribute().value().isClientSyncable()).collect(Collectors.toList());
    }

    @Nullable
    public AttributeInstance getInstance(Holder<Attribute> p_250010_) {
        return this.attributes.computeIfAbsent(p_250010_, p_315936_ -> this.supplier.createInstance(this::onAttributeModified, (Holder<Attribute>)p_315936_));
    }

    public boolean hasAttribute(Holder<Attribute> p_248893_) {
        return this.attributes.get(p_248893_) != null || this.supplier.hasAttribute(p_248893_);
    }

    public boolean hasModifier(Holder<Attribute> p_250299_, ResourceLocation p_350963_) {
        AttributeInstance attributeinstance = this.attributes.get(p_250299_);
        return attributeinstance != null ? attributeinstance.getModifier(p_350963_) != null : this.supplier.hasModifier(p_250299_, p_350963_);
    }

    public double getValue(Holder<Attribute> p_316690_) {
        AttributeInstance attributeinstance = this.attributes.get(p_316690_);
        return attributeinstance != null ? attributeinstance.getValue() : this.supplier.getValue(p_316690_);
    }

    public double getBaseValue(Holder<Attribute> p_316270_) {
        AttributeInstance attributeinstance = this.attributes.get(p_316270_);
        return attributeinstance != null ? attributeinstance.getBaseValue() : this.supplier.getBaseValue(p_316270_);
    }

    public double getModifierValue(Holder<Attribute> p_251534_, ResourceLocation p_350464_) {
        AttributeInstance attributeinstance = this.attributes.get(p_251534_);
        return attributeinstance != null ? attributeinstance.getModifier(p_350464_).amount() : this.supplier.getModifierValue(p_251534_, p_350464_);
    }

    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> p_345244_) {
        p_345244_.forEach((p_351795_, p_351796_) -> {
            AttributeInstance attributeinstance = this.getInstance((Holder<Attribute>)p_351795_);
            if (attributeinstance != null) {
                attributeinstance.removeModifier(p_351796_.id());
                attributeinstance.addTransientModifier(p_351796_);
            }
        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> p_344776_) {
        p_344776_.asMap().forEach((p_344297_, p_344298_) -> {
            AttributeInstance attributeinstance = this.attributes.get(p_344297_);
            if (attributeinstance != null) {
                p_344298_.forEach(p_351794_ -> attributeinstance.removeModifier(p_351794_.id()));
            }
        });
    }

    public void assignAllValues(AttributeMap p_348501_) {
        p_348501_.attributes.values().forEach(p_315934_ -> {
            AttributeInstance attributeinstance = this.getInstance(p_315934_.getAttribute());
            if (attributeinstance != null) {
                attributeinstance.replaceFrom(p_315934_);
            }
        });
    }

    public void assignBaseValues(AttributeMap p_22160_) {
        p_22160_.attributes.values().forEach(p_348165_ -> {
            AttributeInstance attributeinstance = this.getInstance(p_348165_.getAttribute());
            if (attributeinstance != null) {
                attributeinstance.setBaseValue(p_348165_.getBaseValue());
            }
        });
    }

    public ListTag save() {
        ListTag listtag = new ListTag();

        for (AttributeInstance attributeinstance : this.attributes.values()) {
            listtag.add(attributeinstance.save());
        }

        return listtag;
    }

    public void load(ListTag p_22169_) {
        for (int i = 0; i < p_22169_.size(); i++) {
            CompoundTag compoundtag = p_22169_.getCompound(i);
            String s = compoundtag.getString("id");
            ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
            if (resourcelocation != null) {
                Util.ifElse(BuiltInRegistries.ATTRIBUTE.getHolder(resourcelocation), p_315940_ -> {
                    AttributeInstance attributeinstance = this.getInstance(p_315940_);
                    if (attributeinstance != null) {
                        attributeinstance.load(compoundtag);
                    }
                }, () -> LOGGER.warn("Ignoring unknown attribute '{}'", resourcelocation));
            } else {
                LOGGER.warn("Ignoring malformed attribute '{}'", s);
            }
        }
    }
}
