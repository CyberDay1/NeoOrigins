package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.inventory.SlotRanges;

public record SlotsPredicate(Map<SlotRange, ItemPredicate> slots) {
    public static final Codec<SlotsPredicate> CODEC = Codec.unboundedMap(SlotRanges.CODEC, ItemPredicate.CODEC)
        .xmap(SlotsPredicate::new, SlotsPredicate::slots);

    public boolean matches(Entity p_332796_) {
        for (Entry<SlotRange, ItemPredicate> entry : this.slots.entrySet()) {
            if (!matchSlots(p_332796_, entry.getValue(), entry.getKey().slots())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchSlots(Entity p_332687_, ItemPredicate p_332814_, IntList p_332676_) {
        for (int i = 0; i < p_332676_.size(); i++) {
            int j = p_332676_.getInt(i);
            SlotAccess slotaccess = p_332687_.getSlot(j);
            if (p_332814_.test(slotaccess.get())) {
                return true;
            }
        }

        return false;
    }
}
