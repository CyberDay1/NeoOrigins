package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;

public class ThrowablePotionItem extends PotionItem implements ProjectileItem {
    public ThrowablePotionItem(Item.Properties p_43301_) {
        super(p_43301_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_43303_, Player p_43304_, InteractionHand p_43305_) {
        ItemStack itemstack = p_43304_.getItemInHand(p_43305_);
        if (!p_43303_.isClientSide) {
            ThrownPotion thrownpotion = new ThrownPotion(p_43303_, p_43304_);
            thrownpotion.setItem(itemstack);
            thrownpotion.shootFromRotation(p_43304_, p_43304_.getXRot(), p_43304_.getYRot(), -20.0F, 0.5F, 1.0F);
            p_43303_.addFreshEntity(thrownpotion);
        }

        p_43304_.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, p_43304_);
        return InteractionResultHolder.sidedSuccess(itemstack, p_43303_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338465_, Position p_338661_, ItemStack p_338506_, Direction p_338517_) {
        ThrownPotion thrownpotion = new ThrownPotion(p_338465_, p_338661_.x(), p_338661_.y(), p_338661_.z());
        thrownpotion.setItem(p_338506_);
        return thrownpotion;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
