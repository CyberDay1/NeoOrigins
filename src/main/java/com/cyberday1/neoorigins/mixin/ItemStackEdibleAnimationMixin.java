package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.EdibleItemPower;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Returns {@link UseAnim#EAT} from {@link ItemStack#getUseAnimation()} for any
 * stack registered as an edible item by some {@link EdibleItemPower}. Without
 * this, vanilla returns {@link UseAnim#NONE} for non-food items, so the chew
 * animation never plays even when the player is using the item — only the
 * use-related slowdown shows.
 *
 * <p>Vanilla's {@code getUseAnimation()} has no entity context, so we can't
 * gate this per-player. Instead we always return EAT for items that any
 * loaded {@code edible_item} power lists. That's safe: non-power players
 * can't reach the using-item state for these items because their
 * {@link ItemStack#getUseDuration} stays at 0 (gated by
 * {@link ItemStackEdibleDurationMixin} on the server side).
 *
 * <p>Cache invalidates when the powers map identity changes (datapack reload)
 * so the iteration cost is paid once per reload rather than per render frame.
 *
 * <p>{@code require = 0} so the mixin no-ops if a future MC version refactors
 * the method.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEdibleAnimationMixin {

    private static volatile Set<Item> NEOORIGINS$CACHED_EDIBLE_ITEMS = null;
    private static volatile Map<?, ?> NEOORIGINS$CACHED_FOR_MAP = null;

    private static Set<Item> neoorigins$ediblesItems() {
        Map<?, ?> currentMap = PowerDataManager.INSTANCE.getAllPowers();
        if (NEOORIGINS$CACHED_EDIBLE_ITEMS != null && NEOORIGINS$CACHED_FOR_MAP == currentMap) {
            return NEOORIGINS$CACHED_EDIBLE_ITEMS;
        }
        Set<Item> items = new HashSet<>();
        for (var holder : PowerDataManager.INSTANCE.getAllPowers().values()) {
            if (holder.config() instanceof EdibleItemPower.Config cfg) {
                for (var id : cfg.items()) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (item != null) items.add(item);
                }
            }
        }
        NEOORIGINS$CACHED_EDIBLE_ITEMS = items;
        NEOORIGINS$CACHED_FOR_MAP = currentMap;
        return items;
    }

    @Inject(
        method = "getUseAnimation",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void neoorigins$edibleItemAnimation(CallbackInfoReturnable<UseAnim> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.isEmpty()) return;
        if (neoorigins$ediblesItems().contains(self.getItem())) {
            cir.setReturnValue(UseAnim.EAT);
        }
    }
}
