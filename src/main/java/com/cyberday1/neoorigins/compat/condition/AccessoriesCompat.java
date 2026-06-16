package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Isolated, typed bridge to the Accessories (Wisp Forest) API.
 *
 * <p>Every symbol here resolves against the compile-only {@code accessories}
 * dependency, so this class is <b>only ever classloaded</b> behind a
 * {@code ModList.isLoaded("accessories")} gate — mirroring the FTBQ reward
 * registration pattern ({@link com.cyberday1.neoorigins.compat.ftbquests.FtbQuestsRewardRegistration}).
 * The aggregating {@link AccessoryInspector} is the sole caller and performs
 * that gate before touching any method here, so a runtime without Accessories
 * never triggers a {@code NoClassDefFoundError}.
 *
 * <p>API surface confirmed against {@code accessories-neoforge-1.1.0-beta.53+1.21.1}
 * via {@code javap}:
 * <ul>
 *   <li>{@code AccessoriesCapability.get(LivingEntity)} → capability (nullable)</li>
 *   <li>{@code capability.getAllEquipped()} → {@code List<SlotEntryReference>}</li>
 *   <li>{@code SlotEntryReference.stack()} → the equipped {@link ItemStack}</li>
 *   <li>{@code SlotEntryReference.reference().slotName()} → the slot type name
 *       (e.g. {@code ring}, {@code belt}, {@code hands})</li>
 * </ul>
 */
final class AccessoriesCompat {

    private AccessoriesCompat() {}

    /** Fail-closed flag: once the Accessories API throws, stop retrying. */
    private static boolean ACCESSORIES_FAILED = false;

    /**
     * Collects the equipped Accessories stacks for {@code entity}, optionally
     * filtered to a slot type. {@code slotType == null} → every equipped stack;
     * non-null → only stacks whose {@code slotName()} matches (case-insensitive).
     *
     * <p>Must only be called when {@code accessories} is loaded. Fail-closed:
     * any API exception logs once and returns whatever was gathered so far.
     */
    static List<ItemStack> getEquipped(LivingEntity entity, String slotType) {
        List<ItemStack> out = new ArrayList<>();
        if (ACCESSORIES_FAILED) return out;
        try {
            AccessoriesCapability cap = AccessoriesCapability.get(entity);
            if (cap == null) return out;
            for (SlotEntryReference ref : cap.getAllEquipped()) {
                ItemStack stack = ref.stack();
                if (stack == null || stack.isEmpty()) continue;
                if (slotType != null) {
                    String name = ref.reference() != null ? ref.reference().slotName() : null;
                    if (name == null || !name.equalsIgnoreCase(slotType)) continue;
                }
                out.add(stack);
            }
        } catch (Throwable t) {
            // Accessories API absent or changed — disable further attempts.
            ACCESSORIES_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Accessories equipped-stack inspection failed ({}); "
                + "Accessories slots will be treated as empty for equipped_item/umbrella checks.",
                t.toString());
        }
        return out;
    }

    /** One equipped Accessories stack with the slot identity needed to re-equip it. */
    record Entry(String slotName, int slot, ItemStack stack) {}

    /**
     * Enumerates every equipped Accessories stack together with its slot name and
     * index, so a caller (keep_inventory) can clear it on death and re-equip it on
     * respawn. Must only be called when {@code accessories} is loaded; fail-closed.
     */
    static List<Entry> getEquippedEntries(LivingEntity entity) {
        List<Entry> out = new ArrayList<>();
        if (ACCESSORIES_FAILED) return out;
        try {
            AccessoriesCapability cap = AccessoriesCapability.get(entity);
            if (cap == null) return out;
            for (SlotEntryReference ref : cap.getAllEquipped()) {
                ItemStack stack = ref.stack();
                if (stack == null || stack.isEmpty()) continue;
                SlotReference sr = ref.reference();
                if (sr == null || sr.slotName() == null) continue;
                out.add(new Entry(sr.slotName(), sr.slot(), stack));
            }
        } catch (Throwable t) {
            ACCESSORIES_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Accessories slot enumeration failed ({}); "
                + "keep_inventory will not retain Accessories slots.", t.toString());
        }
        return out;
    }

    /**
     * Empties an Accessories slot (used at death once the stack is stashed).
     * Returns true on success. Fail-closed.
     */
    static boolean clearSlot(LivingEntity entity, String slotName, int slot) {
        if (ACCESSORIES_FAILED) return false;
        try {
            SlotReference ref = SlotReference.of(entity, slotName, slot);
            if (!ref.isValid()) return false;
            return ref.setStack(ItemStack.EMPTY);
        } catch (Throwable t) {
            ACCESSORIES_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Accessories slot clear failed ({}).", t.toString());
            return false;
        }
    }

    /**
     * Re-equips {@code stack} into an Accessories slot on respawn, but only if the
     * slot is currently empty (Accessories may have restored its own kept items
     * first). Returns true when the stack was placed. Fail-closed.
     */
    static boolean restoreSlot(LivingEntity entity, String slotName, int slot, ItemStack stack) {
        if (ACCESSORIES_FAILED) return false;
        try {
            SlotReference ref = SlotReference.of(entity, slotName, slot);
            if (!ref.isValid()) return false;
            if (!ref.getStack().isEmpty()) return false;
            return ref.setStack(stack);
        } catch (Throwable t) {
            ACCESSORIES_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Accessories slot restore failed ({}).", t.toString());
            return false;
        }
    }
}
