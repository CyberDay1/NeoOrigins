package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MOD_TRADE_PRICE — per-player adjustment of merchant cost-A counts via the
 * vanilla {@code specialPriceDiff} channel (the same knob Hero of the Village
 * and villager reputation use).
 *
 * <p>Targets {@link AbstractVillager#setTradingPlayer(Player)}, which is the
 * common open/close hook for both villagers and wandering traders. For a
 * {@link net.minecraft.world.entity.npc.Villager}, vanilla's
 * {@code startTrading} runs {@code updateSpecialPrices} (reputation + hero
 * discounts) <em>before</em> calling {@code super.setTradingPlayer}, so by the
 * time our TAIL inject fires the displayed cost already reflects those
 * discounts — our modifier therefore stacks on top of the natural price.
 *
 * <p>Wandering traders never reset {@code specialPriceDiff} on close (only
 * {@link net.minecraft.world.entity.npc.Villager} does, via
 * {@code resetSpecialPrices}). Without a reset our delta would compound across
 * repeated opens of the same trader, so the HEAD inject zeroes the diffs on the
 * close call (player == null). This mirrors the villager reset for everyone and
 * is harmless for villagers (vanilla resets again immediately after).
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradePriceMixin {

    /**
     * Close path — reset our prior contribution so the next open recomputes
     * from a clean baseline (hero/reputation for villagers, zero for traders).
     */
    @Inject(method = "setTradingPlayer", at = @At("HEAD"))
    private void neoorigins$resetTradePrice(Player player, CallbackInfo ci) {
        if (player != null) return;
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.level().isClientSide()) return;
        if (self.getTradingPlayer() == null) return; // not an actual close
        for (MerchantOffer offer : self.getOffers()) {
            offer.resetSpecialPriceDiff();
        }
    }

    /**
     * Open path — apply MOD_TRADE_PRICE to each offer's displayed cost-A count.
     * The chained modifier is treated as the new absolute count; we fold the
     * difference into {@code specialPriceDiff} so both the client display and
     * server-side purchase validation see the adjusted price.
     */
    @Inject(method = "setTradingPlayer", at = @At("TAIL"))
    private void neoorigins$applyTradePrice(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        AbstractVillager self = (AbstractVillager) (Object) this;
        if (self.level().isClientSide()) return;
        MerchantOffers offers = self.getOffers();
        if (offers.isEmpty()) return;
        for (MerchantOffer offer : offers) {
            int displayed = offer.getCostA().getCount();
            float scaled = EventPowerIndex.dispatchModifier(sp,
                EventPowerIndex.Event.MOD_TRADE_PRICE,
                new EventPowerIndex.TradeContext(offer), displayed);
            if (!Float.isFinite(scaled)) continue;
            int max = offer.getBaseCostA().getMaxStackSize();
            int desired = Mth.clamp(Math.round(scaled), 1, max);
            if (desired != displayed) {
                offer.addToSpecialPriceDiff(desired - displayed);
            }
        }
    }
}
