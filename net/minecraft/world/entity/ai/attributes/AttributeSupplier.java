package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

public class AttributeSupplier {
    private final Map<Holder<Attribute>, AttributeInstance> instances;

    AttributeSupplier(Map<Holder<Attribute>, AttributeInstance> p_22243_) {
        this.instances = p_22243_;
    }

    private AttributeInstance getAttributeInstance(Holder<Attribute> p_316859_) {
        AttributeInstance attributeinstance = this.instances.get(p_316859_);
        if (attributeinstance == null) {
            throw new IllegalArgumentException("Can't find attribute " + p_316859_.getRegisteredName());
        } else {
            return attributeinstance;
        }
    }

    public double getValue(Holder<Attribute> p_316336_) {
        return this.getAttributeInstance(p_316336_).getValue();
    }

    public double getBaseValue(Holder<Attribute> p_316396_) {
        return this.getAttributeInstance(p_316396_).getBaseValue();
    }

    public double getModifierValue(Holder<Attribute> p_316754_, ResourceLocation p_350708_) {
        AttributeModifier attributemodifier = this.getAttributeInstance(p_316754_).getModifier(p_350708_);
        if (attributemodifier == null) {
            throw new IllegalArgumentException("Can't find modifier " + p_350708_ + " on attribute " + p_316754_.getRegisteredName());
        } else {
            return attributemodifier.amount();
        }
    }

    @Nullable
    public AttributeInstance createInstance(Consumer<AttributeInstance> p_22251_, Holder<Attribute> p_316258_) {
        AttributeInstance attributeinstance = this.instances.get(p_316258_);
        if (attributeinstance == null) {
            return null;
        } else {
            AttributeInstance attributeinstance1 = new AttributeInstance(p_316258_, p_22251_);
            attributeinstance1.replaceFrom(attributeinstance);
            return attributeinstance1;
        }
    }

    public static AttributeSupplier.Builder builder() {
        return new AttributeSupplier.Builder();
    }

    public boolean hasAttribute(Holder<Attribute> p_316617_) {
        return this.instances.containsKey(p_316617_);
    }

    public boolean hasModifier(Holder<Attribute> p_316471_, ResourceLocation p_350394_) {
        AttributeInstance attributeinstance = this.instances.get(p_316471_);
        return attributeinstance != null && attributeinstance.getModifier(p_350394_) != null;
    }

    public static class Builder {
        private final Map<Holder<Attribute>, AttributeInstance> builder = new java.util.HashMap<>();
        private boolean instanceFrozen;
        private final java.util.List<AttributeSupplier.Builder> others = new java.util.ArrayList<>();

        public Builder() { }

        public Builder(AttributeSupplier attributeMap) {
            this.builder.putAll(attributeMap.instances);
        }

        public void combine(Builder other) {
            this.builder.putAll(other.builder);
            others.add(other);
        }

        public boolean hasAttribute(Holder<Attribute> attribute) {
            return this.builder.containsKey(attribute);
        }

        private AttributeInstance create(Holder<Attribute> p_316369_) {
            AttributeInstance attributeinstance = new AttributeInstance(p_316369_, p_315942_ -> {
                if (this.instanceFrozen) {
                    throw new UnsupportedOperationException("Tried to change value for default attribute instance: " + p_316369_.getRegisteredName());
                }
            });
            this.builder.put(p_316369_, attributeinstance);
            return attributeinstance;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> p_316658_) {
            this.create(p_316658_);
            return this;
        }

        public AttributeSupplier.Builder add(Holder<Attribute> p_316875_, double p_22270_) {
            AttributeInstance attributeinstance = this.create(p_316875_);
            attributeinstance.setBaseValue(p_22270_);
            return this;
        }

        public AttributeSupplier build() {
            this.instanceFrozen = true;
            others.forEach(b -> b.instanceFrozen = true);
            return new AttributeSupplier(ImmutableMap.copyOf(this.builder));
        }
    }
}
