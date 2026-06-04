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

    @SuppressWarnings("unchecked")
    private static List<ItemStack> neoorigins$getCurios(LivingEntity entity, String slotType) {
        List<ItemStack> out = new ArrayList<>();
        if (CURIOS_REFLECT_FAILED) return out;
        try {
            if (CURIOS_GET_INVENTORY == null) {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                CURIOS_GET_INVENTORY = api.getMethod("getCuriosInventory", LivingEntity.class);
                Class<?> handlerClass = Class.forName(
                    "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                CURIOS_GET_EQUIPPED = handlerClass.getMethod("getEquippedCurios");
                CURIOS_GET_CURIOS = handlerClass.getMethod("getCurios");
                Class<?> stacksClass = Class.forName(
                    "top.theillusivec4.curios.api.type.ICurioStacksHandler");
                CURIO_STACKS_GET_STACKS = stacksClass.getMethod("getStacks");
            }
            // CuriosApi.getCuriosInventory(entity) -> Optional<ICuriosItemHandler>
            java.util.Optional<?> opt = (java.util.Optional<?>) CURIOS_GET_INVENTORY.invoke(null, entity);
            if (opt.isEmpty()) return out;
            Object handler = opt.get();

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
