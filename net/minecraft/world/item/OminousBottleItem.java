package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public class OminousBottleItem extends Item {
    private static final int DRINK_DURATION = 32;
    public static final int EFFECT_DURATION = 120000;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 4;

    public OminousBottleItem(Item.Properties p_338721_) {
        super(p_338721_);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack p_338871_, Level p_338693_, LivingEntity p_338370_) {
        if (p_338370_ instanceof ServerPlayer serverplayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverplayer, p_338871_);
            serverplayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (!p_338693_.isClientSide) {
            p_338693_.playSound(null, p_338370_.blockPosition(), SoundEvents.OMINOUS_BOTTLE_DISPOSE, p_338370_.getSoundSource(), 1.0F, 1.0F);
            Integer integer = p_338871_.getOrDefault(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, Integer.valueOf(0));
            p_338370_.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 120000, integer, false, false, true));
        }

        p_338871_.consume(1, p_338370_);
        return p_338871_;
    }

    @Override
    public int getUseDuration(ItemStack p_338822_, LivingEntity p_345182_) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack p_338722_) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_338229_, Player p_338350_, InteractionHand p_338729_) {
        return ItemUtils.startUsingInstantly(p_338229_, p_338350_, p_338729_);
    }

    @Override
    public void appendHoverText(ItemStack p_338470_, Item.TooltipContext p_339656_, List<Component> p_338463_, TooltipFlag p_338317_) {
        super.appendHoverText(p_338470_, p_339656_, p_338463_, p_338317_);
        Integer integer = p_338470_.getOrDefault(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, Integer.valueOf(0));
        List<MobEffectInstance> list = List.of(new MobEffectInstance(MobEffects.BAD_OMEN, 120000, integer, false, false, true));
        PotionContents.addPotionTooltip(list, p_338463_::add, 1.0F, p_339656_.tickRate());
    }
}
