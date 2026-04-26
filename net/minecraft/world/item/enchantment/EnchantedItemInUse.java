package net.minecraft.world.item.enchantment;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record EnchantedItemInUse(ItemStack itemStack, @Nullable EquipmentSlot inSlot, @Nullable LivingEntity owner, Consumer<Item> onBreak) {
    public EnchantedItemInUse(ItemStack p_344800_, EquipmentSlot p_344722_, LivingEntity p_345536_) {
        this(p_344800_, p_344722_, p_345536_, p_348392_ -> p_345536_.onEquippedItemBroken(p_348392_, p_344722_));
    }
}
