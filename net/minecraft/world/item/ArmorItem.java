package net.minecraft.world.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public class ArmorItem extends Item implements Equipable {
    public static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource p_302434_, ItemStack p_40409_) {
            return ArmorItem.dispenseArmor(p_302434_, p_40409_) ? p_40409_ : super.execute(p_302434_, p_40409_);
        }
    };
    protected final ArmorItem.Type type;
    protected final Holder<ArmorMaterial> material;
    private final Supplier<ItemAttributeModifiers> defaultModifiers;

    public static boolean dispenseArmor(BlockSource p_302421_, ItemStack p_40400_) {
        BlockPos blockpos = p_302421_.pos().relative(p_302421_.state().getValue(DispenserBlock.FACING));
        List<LivingEntity> list = p_302421_.level()
            .getEntitiesOfClass(
                LivingEntity.class, new AABB(blockpos), EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(p_40400_))
            );
        if (list.isEmpty()) {
            return false;
        } else {
            LivingEntity livingentity = list.get(0);
            EquipmentSlot equipmentslot = livingentity.getEquipmentSlotForItem(p_40400_);
            // Neo: Respect IItemExtension#canEquip in dispenseArmor
            if (!p_40400_.canEquip(equipmentslot, livingentity)) return false;
            ItemStack itemstack = p_40400_.split(1);
            livingentity.setItemSlot(equipmentslot, itemstack);
            if (livingentity instanceof Mob) {
                ((Mob)livingentity).setDropChance(equipmentslot, 2.0F);
                ((Mob)livingentity).setPersistenceRequired();
            }

            return true;
        }
    }

    public ArmorItem(Holder<ArmorMaterial> p_323783_, ArmorItem.Type p_266831_, Item.Properties p_40388_) {
        super(p_40388_);
        this.material = p_323783_;
        this.type = p_266831_;
        DispenserBlock.registerBehavior(this, DISPENSE_ITEM_BEHAVIOR);
        this.defaultModifiers = Suppliers.memoize(
            () -> {
                int i = p_323783_.value().getDefense(p_266831_);
                float f = p_323783_.value().toughness();
                ItemAttributeModifiers.Builder itemattributemodifiers$builder = ItemAttributeModifiers.builder();
                EquipmentSlotGroup equipmentslotgroup = EquipmentSlotGroup.bySlot(p_266831_.getSlot());
                ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace("armor." + p_266831_.getName());
                itemattributemodifiers$builder.add(
                    Attributes.ARMOR, new AttributeModifier(resourcelocation, (double)i, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup
                );
                itemattributemodifiers$builder.add(
                    Attributes.ARMOR_TOUGHNESS, new AttributeModifier(resourcelocation, (double)f, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup
                );
                float f1 = p_323783_.value().knockbackResistance();
                if (f1 > 0.0F) {
                    itemattributemodifiers$builder.add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(resourcelocation, (double)f1, AttributeModifier.Operation.ADD_VALUE),
                        equipmentslotgroup
                    );
                }

                return itemattributemodifiers$builder.build();
            }
        );
    }

    public ArmorItem.Type getType() {
        return this.type;
    }

    @Override
    public int getEnchantmentValue() {
        return this.material.value().enchantmentValue();
    }

    public Holder<ArmorMaterial> getMaterial() {
        return this.material;
    }

    @Override
    public boolean isValidRepairItem(ItemStack p_40392_, ItemStack p_40393_) {
        return this.material.value().repairIngredient().get().test(p_40393_) || super.isValidRepairItem(p_40392_, p_40393_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_40395_, Player p_40396_, InteractionHand p_40397_) {
        return this.swapWithEquipmentSlot(this, p_40395_, p_40396_, p_40397_);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return this.defaultModifiers.get();
    }

    public int getDefense() {
        return this.material.value().getDefense(this.type);
    }

    public float getToughness() {
        return this.material.value().toughness();
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return this.type.getSlot();
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return this.getMaterial().value().equipSound();
    }

    public static enum Type implements StringRepresentable {
        HELMET(EquipmentSlot.HEAD, 11, "helmet"),
        CHESTPLATE(EquipmentSlot.CHEST, 16, "chestplate"),
        LEGGINGS(EquipmentSlot.LEGS, 15, "leggings"),
        BOOTS(EquipmentSlot.FEET, 13, "boots"),
        BODY(EquipmentSlot.BODY, 16, "body");

        public static final Codec<ArmorItem.Type> CODEC = StringRepresentable.fromValues(ArmorItem.Type::values);
        private final EquipmentSlot slot;
        private final String name;
        private final int durability;

        private Type(EquipmentSlot p_266754_, int p_332191_, String p_266886_) {
            this.slot = p_266754_;
            this.name = p_266886_;
            this.durability = p_332191_;
        }

        public int getDurability(int p_323808_) {
            return this.durability * p_323808_;
        }

        public EquipmentSlot getSlot() {
            return this.slot;
        }

        public String getName() {
            return this.name;
        }

        public boolean hasTrims() {
            return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
