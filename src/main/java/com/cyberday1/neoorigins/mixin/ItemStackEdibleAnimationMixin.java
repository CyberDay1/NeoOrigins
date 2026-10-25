package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.client.ClientTagEpoch;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.EdibleItemPower;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
 * <p>Cache is keyed on the power-set version plus the client tag epoch, so
 * the iteration cost is paid once per reload or tag sync rather than per
 * render frame (#136: Punchy calls this several times a frame).
 *
 * <p>{@code require = 0} so the mixin no-ops if a future MC version refactors
 * the method.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEdibleAnimationMixin {

    // One volatile pair so a reader never sees a set built for a different key.
    private static volatile Map.Entry<Long, Set<Item>> NEOORIGINS$CACHE = null;

    private static Set<Item> neoorigins$ediblesItems() {
        long key = ((long) PowerDataManager.INSTANCE.version() << 32) | (ClientTagEpoch.get() & 0xFFFFFFFFL);
        Map.Entry<Long, Set<Item>> cached = NEOORIGINS$CACHE;
        if (cached != null && cached.getKey() == key) {
            return cached.getValue();
        }
        Set<Item> items = new HashSet<>();
        for (var holder : PowerDataManager.INSTANCE.getAllPowers().values()) {
            if (holder.config() instanceof EdibleItemPower.Config cfg) {
                for (var id : cfg.items()) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (item != null) items.add(item);
                }
                for (ResourceLocation tagId : cfg.tags()) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                    BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> items.add(h.value()));
                }
            }
        }
        NEOORIGINS$CACHE = Map.entry(key, items);
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
