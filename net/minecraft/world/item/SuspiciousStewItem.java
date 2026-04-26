package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;

public class SuspiciousStewItem extends Item {
    public static final int DEFAULT_DURATION = 160;

    public SuspiciousStewItem(Item.Properties p_43257_) {
        super(p_43257_);
    }

    @Override
    public void appendHoverText(ItemStack p_260314_, Item.TooltipContext p_339691_, List<Component> p_259700_, TooltipFlag p_260021_) {
        super.appendHoverText(p_260314_, p_339691_, p_259700_, p_260021_);
        if (p_260021_.isCreative()) {
            List<MobEffectInstance> list = new ArrayList<>();
            SuspiciousStewEffects suspicioussteweffects = p_260314_.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);

            for (SuspiciousStewEffects.Entry suspicioussteweffects$entry : suspicioussteweffects.effects()) {
                list.add(suspicioussteweffects$entry.createEffectInstance());
            }

            PotionContents.addPotionTooltip(list, p_259700_::add, 1.0F, p_339691_.tickRate());
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack p_43263_, Level p_43264_, LivingEntity p_43265_) {
        SuspiciousStewEffects suspicioussteweffects = p_43263_.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);

        for (SuspiciousStewEffects.Entry suspicioussteweffects$entry : suspicioussteweffects.effects()) {
            p_43265_.addEffect(suspicioussteweffects$entry.createEffectInstance());
        }

        return super.finishUsingItem(p_43263_, p_43264_, p_43265_);
    }
}
