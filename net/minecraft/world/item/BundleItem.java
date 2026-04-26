package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

public class BundleItem extends Item {
    private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);
    private static final int TOOLTIP_MAX_WEIGHT = 64;

    public BundleItem(Item.Properties p_150726_) {
        super(p_150726_);
    }

    public static float getFullnessDisplay(ItemStack p_150767_) {
        BundleContents bundlecontents = p_150767_.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundlecontents.weight().floatValue();
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack p_150733_, Slot p_150734_, ClickAction p_150735_, Player p_150736_) {
        if (p_150733_.getCount() != 1 || p_150735_ != ClickAction.SECONDARY) {
            return false;
        } else {
            BundleContents bundlecontents = p_150733_.get(DataComponents.BUNDLE_CONTENTS);
            if (bundlecontents == null) {
                return false;
            } else {
                ItemStack itemstack = p_150734_.getItem();
                BundleContents.Mutable bundlecontents$mutable = new BundleContents.Mutable(bundlecontents);
                if (itemstack.isEmpty()) {
                    this.playRemoveOneSound(p_150736_);
                    ItemStack itemstack1 = bundlecontents$mutable.removeOne();
                    if (itemstack1 != null) {
                        ItemStack itemstack2 = p_150734_.safeInsert(itemstack1);
                        bundlecontents$mutable.tryInsert(itemstack2);
                    }
                } else if (itemstack.canFitInsideContainerItems()) { // Neo: stack-aware placeability check
                    int i = bundlecontents$mutable.tryTransfer(p_150734_, p_150736_);
                    if (i > 0) {
                        this.playInsertSound(p_150736_);
                    }
                }

                p_150733_.set(DataComponents.BUNDLE_CONTENTS, bundlecontents$mutable.toImmutable());
                return true;
            }
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(
        ItemStack p_150742_, ItemStack p_150743_, Slot p_150744_, ClickAction p_150745_, Player p_150746_, SlotAccess p_150747_
    ) {
        if (p_150742_.getCount() != 1) return false;
        if (p_150745_ == ClickAction.SECONDARY && p_150744_.allowModification(p_150746_)) {
            BundleContents bundlecontents = p_150742_.get(DataComponents.BUNDLE_CONTENTS);
            if (bundlecontents == null) {
                return false;
            } else {
                BundleContents.Mutable bundlecontents$mutable = new BundleContents.Mutable(bundlecontents);
                if (p_150743_.isEmpty()) {
                    ItemStack itemstack = bundlecontents$mutable.removeOne();
                    if (itemstack != null) {
                        this.playRemoveOneSound(p_150746_);
                        p_150747_.set(itemstack);
                    }
                } else {
                    int i = bundlecontents$mutable.tryInsert(p_150743_);
                    if (i > 0) {
                        this.playInsertSound(p_150746_);
                    }
                }

                p_150742_.set(DataComponents.BUNDLE_CONTENTS, bundlecontents$mutable.toImmutable());
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_150760_, Player p_150761_, InteractionHand p_150762_) {
        ItemStack itemstack = p_150761_.getItemInHand(p_150762_);
        if (dropContents(itemstack, p_150761_)) {
            this.playDropContentsSound(p_150761_);
            p_150761_.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(itemstack, p_150760_.isClientSide());
        } else {
            return InteractionResultHolder.fail(itemstack);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack p_150769_) {
        BundleContents bundlecontents = p_150769_.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return bundlecontents.weight().compareTo(Fraction.ZERO) > 0;
    }

    @Override
    public int getBarWidth(ItemStack p_150771_) {
        BundleContents bundlecontents = p_150771_.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return Math.min(1 + Mth.mulAndTruncate(bundlecontents.weight(), 12), 13);
    }

    @Override
    public int getBarColor(ItemStack p_150773_) {
        return BAR_COLOR;
    }

    private static boolean dropContents(ItemStack p_150730_, Player p_150731_) {
        BundleContents bundlecontents = p_150730_.get(DataComponents.BUNDLE_CONTENTS);
        if (bundlecontents != null && !bundlecontents.isEmpty()) {
            p_150730_.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            if (p_150731_ instanceof ServerPlayer) {
                bundlecontents.itemsCopy().forEach(p_330078_ -> p_150731_.drop(p_330078_, true));
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack p_150775_) {
        return !p_150775_.has(DataComponents.HIDE_TOOLTIP) && !p_150775_.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)
            ? Optional.ofNullable(p_150775_.get(DataComponents.BUNDLE_CONTENTS)).map(BundleTooltip::new)
            : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack p_150749_, Item.TooltipContext p_339687_, List<Component> p_150751_, TooltipFlag p_150752_) {
        BundleContents bundlecontents = p_150749_.get(DataComponents.BUNDLE_CONTENTS);
        if (bundlecontents != null) {
            int i = Mth.mulAndTruncate(bundlecontents.weight(), 64);
            p_150751_.add(Component.translatable("item.minecraft.bundle.fullness", i, 64).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void onDestroyed(ItemEntity p_150728_) {
        BundleContents bundlecontents = p_150728_.getItem().get(DataComponents.BUNDLE_CONTENTS);
        if (bundlecontents != null) {
            p_150728_.getItem().set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            ItemUtils.onContainerDestroyed(p_150728_, bundlecontents.itemsCopy());
        }
    }

    private void playRemoveOneSound(Entity p_186343_) {
        p_186343_.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + p_186343_.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity p_186352_) {
        p_186352_.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + p_186352_.level().getRandom().nextFloat() * 0.4F);
    }

    private void playDropContentsSound(Entity p_186354_) {
        p_186354_.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + p_186354_.level().getRandom().nextFloat() * 0.4F);
    }
}
