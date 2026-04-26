package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MilkBucketItem extends Item {
    private static final int DRINK_DURATION = 32;

    public MilkBucketItem(Item.Properties p_42921_) {
        super(p_42921_);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack p_42923_, Level p_42924_, LivingEntity p_42925_) {
        if (p_42925_ instanceof ServerPlayer serverplayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverplayer, p_42923_);
            serverplayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (!p_42924_.isClientSide) {
            p_42925_.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.MILK);
        }

        if (p_42925_ instanceof Player player) {
            return ItemUtils.createFilledResult(p_42923_, player, new ItemStack(Items.BUCKET), false);
        } else {
            p_42923_.consume(1, p_42925_);
            return p_42923_;
        }
    }

    @Override
    public int getUseDuration(ItemStack p_42933_, LivingEntity p_345727_) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack p_42931_) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_42927_, Player p_42928_, InteractionHand p_42929_) {
        return ItemUtils.startUsingInstantly(p_42927_, p_42928_, p_42929_);
    }
}
