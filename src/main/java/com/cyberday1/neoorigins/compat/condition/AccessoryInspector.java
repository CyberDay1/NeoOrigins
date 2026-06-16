package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared inspector that aggregates a living entity's equipped <i>accessory</i>
 * stacks across both supported "wearable trinket" mods, each independently
 * gated and fail-closed:
 *
 * <ul>
 *   <li><b>Curios</b> (mod id {@code curios}) — resolved at runtime via
 *       reflection (Curios is not a compile dependency). This is the logic
 *       formerly inlined in {@link ConditionParser} for umbrella detection,
 *       extracted here so the umbrella path and the {@code equipped_item}
 *       {@code accessory} slot share one implementation.</li>
 *   <li><b>Accessories</b> (mod id {@code accessories}, Wisp Forest) — resolved
 *       through the typed, isolated {@link AccessoriesCompat} bridge, which is
 *       only classloaded behind the {@code accessories} mod gate.</li>
 * </ul>
 *
 * <p>If neither mod is present the inspector returns an empty list. Every
 * source is fail-closed: a reflection/API failure logs once and is treated as
 * "no accessories" rather than throwing.
 */
public final class AccessoryInspector {

    private AccessoryInspector() {}

    private static final boolean CURIOS_LOADED = neoorigins$modLoaded("curios");
    private static final boolean ACCESSORIES_LOADED = neoorigins$modLoaded("accessories");

    private static boolean neoorigins$modLoaded(String modId) {
        net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
        return list != null && list.isLoaded(modId);
    }

    /**
     * Returns every equipped accessory stack for {@code entity}, aggregated
     * across Curios and Accessories.
     *
     * @param entity   the living entity to inspect
     * @param slotType when {@code null}, returns all equipped accessory stacks
     *                 from both sources; when non-null, narrows to the named
     *                 slot type only (case-insensitive — matched against the
     *                 Curios slot identifier and the Accessories slot-type name)
     * @return the matching equipped stacks (never null; empty when neither mod
     *         is present or nothing matches)
     */
    public static List<ItemStack> getEquippedAccessories(LivingEntity entity, String slotType) {
        List<ItemStack> out = new ArrayList<>();
        if (entity == null) return out;
        if (CURIOS_LOADED) {
            out.addAll(neoorigins$getCurios(entity, slotType));
        }
        if (ACCESSORIES_LOADED) {
            // Typed call lives behind the accessories gate above; AccessoriesCompat
            // is only classloaded once we know the mod is present.
            out.addAll(AccessoriesCompat.getEquipped(entity, slotType));
        }
        return out;
    }

    // ── Equip/unequip support for keep_inventory ─────────────────────────
    //
    // The death/respawn flow needs more than read access: it has to know each
    // stack's slot identity (to re-equip it) and to write the slot empty on
    // death / re-equip on respawn. These mirror getEquippedAccessories but carry
    // slot name + index, and add clear/restore. Curios goes through reflection;
    // Accessories through the typed bridge.

    /** Which trinket mod a kept slot belongs to. */
    public enum Source { CURIOS, ACCESSORIES }

    /** One equipped trinket stack plus the slot identity needed to re-equip it. */
    public record EquippedEntry(Source source, String slotId, int index, ItemStack stack) {}

    /** Every equipped trinket across both mods, with slot identity for re-equip. */
    public static List<EquippedEntry> getEquippedEntries(LivingEntity entity) {
        List<EquippedEntry> out = new ArrayList<>();
        if (entity == null) return out;
        if (CURIOS_LOADED) out.addAll(neoorigins$getCuriosEntries(entity));
        if (ACCESSORIES_LOADED) {
            for (AccessoriesCompat.Entry e : AccessoriesCompat.getEquippedEntries(entity)) {
                out.add(new EquippedEntry(Source.ACCESSORIES, e.slotName(), e.slot(), e.stack()));
            }
        }
        return out;
    }

    /** Empties a trinket slot once its stack has been stashed (death). */
    public static boolean clearSlot(LivingEntity entity, Source source, String slotId, int index) {
        return switch (source) {
            case CURIOS -> CURIOS_LOADED && neoorigins$writeCurioSlot(entity, slotId, index, ItemStack.EMPTY);
            case ACCESSORIES -> ACCESSORIES_LOADED && AccessoriesCompat.clearSlot(entity, slotId, index);
        };
    }

    /** Re-equips a stashed stack into its original trinket slot if still empty (respawn). */
    public static boolean restoreSlot(LivingEntity entity, Source source, String slotId, int index, ItemStack stack) {
        return switch (source) {
            case CURIOS -> CURIOS_LOADED && neoorigins$restoreCurioSlot(entity, slotId, index, stack);
            case ACCESSORIES -> ACCESSORIES_LOADED && AccessoriesCompat.restoreSlot(entity, slotId, index, stack);
        };
    }

    // ── Curios (reflection) ─────────────────────────────────────────────
    //
    // Curios is not a compile-time dependency, so the API is resolved at
    // runtime. Method handles are cached after the first successful call.
    // Unfiltered queries use the flat ICuriosItemHandler.getEquippedCurios()
    // handler (the path the umbrella check has always used). Slot-filtered
    // queries use getCurios() -> Map<String, ICurioStacksHandler> so we can key
    // on the curio slot identifier and read that identifier's IDynamicStackHandler.

    private static Method CURIOS_GET_INVENTORY;
    private static Method CURIOS_GET_EQUIPPED;
    private static Method CURIOS_GET_CURIOS;
    private static Method CURIO_STACKS_GET_STACKS;
    private static boolean CURIOS_REFLECT_FAILED = false;

    /** Lazily resolves the Curios reflection handles; false once it has failed. */
    private static boolean neoorigins$ensureCuriosReflection() {
        if (CURIOS_REFLECT_FAILED) return false;
        if (CURIOS_GET_INVENTORY != null) return true;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            CURIOS_GET_INVENTORY = api.getMethod("getCuriosInventory", LivingEntity.class);
            Class<?> handlerClass = Class.forName(
                "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            CURIOS_GET_EQUIPPED = handlerClass.getMethod("getEquippedCurios");
            CURIOS_GET_CURIOS = handlerClass.getMethod("getCurios");
            Class<?> stacksClass = Class.forName(
                "top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            CURIO_STACKS_GET_STACKS = stacksClass.getMethod("getStacks");
            return true;
        } catch (Exception e) {
            CURIOS_REFLECT_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Curios API unavailable ({}); Curios slots "
                + "will be treated as empty.", e.toString());
            return false;
        }
    }

    /** Resolves the entity's Curios handler, or null when absent/unavailable. */
    private static Object neoorigins$curiosHandler(LivingEntity entity) {
        if (!neoorigins$ensureCuriosReflection()) return null;
        try {
            java.util.Optional<?> opt = (java.util.Optional<?>) CURIOS_GET_INVENTORY.invoke(null, entity);
            return opt.isPresent() ? opt.get() : null;
        } catch (Exception e) {
            CURIOS_REFLECT_FAILED = true;
            return null;
        }
    }

    /** Resolves the writable per-slot stack handler for a Curios slot id, or null. */
    @SuppressWarnings("unchecked")
    private static net.neoforged.neoforge.items.IItemHandlerModifiable neoorigins$curiosSlotStacks(
            Object handler, String slotId) {
        if (handler == null) return null;
        try {
            Map<String, ?> curios = (Map<String, ?>) CURIOS_GET_CURIOS.invoke(handler);
            for (Map.Entry<String, ?> e : curios.entrySet()) {
                if (!slotId.equalsIgnoreCase(e.getKey())) continue;
                return (net.neoforged.neoforge.items.IItemHandlerModifiable)
                    CURIO_STACKS_GET_STACKS.invoke(e.getValue());
            }
        } catch (Exception e) {
            CURIOS_REFLECT_FAILED = true;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<EquippedEntry> neoorigins$getCuriosEntries(LivingEntity entity) {
        List<EquippedEntry> out = new ArrayList<>();
        Object handler = neoorigins$curiosHandler(entity);
        if (handler == null) return out;
        try {
            Map<String, ?> curios = (Map<String, ?>) CURIOS_GET_CURIOS.invoke(handler);
            for (Map.Entry<String, ?> e : curios.entrySet()) {
                var stacks = (net.neoforged.neoforge.items.IItemHandlerModifiable)
                    CURIO_STACKS_GET_STACKS.invoke(e.getValue());
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) out.add(new EquippedEntry(Source.CURIOS, e.getKey(), i, stack));
                }
            }
        } catch (Exception e) {
            CURIOS_REFLECT_FAILED = true;
        }
        return out;
    }

    /** Writes a Curios slot (empty on death). Returns true on success. */
    private static boolean neoorigins$writeCurioSlot(LivingEntity entity, String slotId, int index, ItemStack stack) {
        var stacks = neoorigins$curiosSlotStacks(neoorigins$curiosHandler(entity), slotId);
        if (stacks == null || index < 0 || index >= stacks.getSlots()) return false;
        stacks.setStackInSlot(index, stack);
        return true;
    }

    /** Re-equips into a Curios slot on respawn, only if currently empty. */
    private static boolean neoorigins$restoreCurioSlot(LivingEntity entity, String slotId, int index, ItemStack stack) {
        var stacks = neoorigins$curiosSlotStacks(neoorigins$curiosHandler(entity), slotId);
        if (stacks == null || index < 0 || index >= stacks.getSlots()) return false;
        if (!stacks.getStackInSlot(index).isEmpty()) return false;
        stacks.setStackInSlot(index, stack);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> neoorigins$getCurios(LivingEntity entity, String slotType) {
        List<ItemStack> out = new ArrayList<>();
        Object handler = neoorigins$curiosHandler(entity);
        if (handler == null) return out;
        try {
            if (slotType == null) {
                // Flat equipped handler — the exact path the umbrella check used.
                var equipped = (net.neoforged.neoforge.items.IItemHandlerModifiable)
                    CURIOS_GET_EQUIPPED.invoke(handler);
                for (int i = 0; i < equipped.getSlots(); i++) {
                    ItemStack stack = equipped.getStackInSlot(i);
                    if (!stack.isEmpty()) out.add(stack);
                }
                return out;
            }

            // Slot-filtered: getCurios() -> Map<String identifier, ICurioStacksHandler>.
            Map<String, ?> curios = (Map<String, ?>) CURIOS_GET_CURIOS.invoke(handler);
            for (Map.Entry<String, ?> e : curios.entrySet()) {
                if (!slotType.equalsIgnoreCase(e.getKey())) continue;
                var stacks = (net.neoforged.neoforge.items.IItemHandlerModifiable)
                    CURIO_STACKS_GET_STACKS.invoke(e.getValue());
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) out.add(stack);
                }
            }
        } catch (Exception e) {
            // Curios API not available or changed — disable further attempts.
            CURIOS_REFLECT_FAILED = true;
            NeoOrigins.LOGGER.warn("[Compat] Curios equipped-stack inspection failed ({}); "
                + "Curios slots will be treated as empty for equipped_item/umbrella checks.",
                e.toString());
        }
        return out;
    }
}
