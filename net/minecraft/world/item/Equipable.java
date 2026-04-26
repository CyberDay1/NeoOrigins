package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public interface Equipable {
    EquipmentSlot getEquipmentSlot();

    default Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    default InteractionResultHolder<ItemStack> swapWithEquipmentSlot(Item p_270453_, Level p_270395_, Player p_270300_, InteractionHand p_270262_) {
        ItemStack itemstack = p_270300_.getItemInHand(p_270262_);
        EquipmentSlot equipmentslot = p_270300_.getEquipmentSlotForItem(itemstack);
        if (!p_270300_.canUseSlot(equipmentslot)) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            ItemStack itemstack1 = p_270300_.getItemBySlot(equipmentslot);
            if ((!EnchantmentHelper.has(itemstack1, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || p_270300_.isCreative())
                && !ItemStack.matches(itemstack, itemstack1)) {
                if (!p_270395_.isClientSide()) {
                    p_270300_.awardStat(Stats.ITEM_USED.get(p_270453_));
                }

                ItemStack itemstack2 = itemstack1.isEmpty() ? itemstack : itemstack1.copyAndClear();
                ItemStack itemstack3 = p_270300_.isCreative() ? itemstack.copy() : itemstack.copyAndClear();
                p_270300_.setItemSlot(equipmentslot, itemstack3);
                return InteractionResultHolder.sidedSuccess(itemstack2, p_270395_.isClientSide());
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    @Nullable
    static Equipable get(ItemStack p_270317_) {
        Item $$3 = p_270317_.getItem();
        if ($$3 instanceof Equipable) {
            return (Equipable)$$3;
        } else {
            if (p_270317_.getItem() instanceof BlockItem blockitem) {
                Block block = blockitem.getBlock();
                if (block instanceof Equipable) {
                    return (Equipable)block;
                }
            }

            return null;
        }
    }
}
