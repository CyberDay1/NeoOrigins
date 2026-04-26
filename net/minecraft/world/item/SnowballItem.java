package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;

public class SnowballItem extends Item implements ProjectileItem {
    public SnowballItem(Item.Properties p_43140_) {
        super(p_43140_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_43142_, Player p_43143_, InteractionHand p_43144_) {
        ItemStack itemstack = p_43143_.getItemInHand(p_43144_);
        p_43142_.playSound(
            null,
            p_43143_.getX(),
            p_43143_.getY(),
            p_43143_.getZ(),
            SoundEvents.SNOWBALL_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (p_43142_.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        if (!p_43142_.isClientSide) {
            Snowball snowball = new Snowball(p_43142_, p_43143_);
            snowball.setItem(itemstack);
            snowball.shootFromRotation(p_43143_, p_43143_.getXRot(), p_43143_.getYRot(), 0.0F, 1.5F, 1.0F);
            p_43142_.addFreshEntity(snowball);
        }

        p_43143_.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, p_43143_);
        return InteractionResultHolder.sidedSuccess(itemstack, p_43142_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338685_, Position p_338637_, ItemStack p_338820_, Direction p_338856_) {
        Snowball snowball = new Snowball(p_338685_, p_338637_.x(), p_338637_.y(), p_338637_.z());
        snowball.setItem(p_338820_);
        return snowball;
    }
}
