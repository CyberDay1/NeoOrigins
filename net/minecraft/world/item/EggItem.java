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
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.Level;

public class EggItem extends Item implements ProjectileItem {
    public EggItem(Item.Properties p_41126_) {
        super(p_41126_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_41128_, Player p_41129_, InteractionHand p_41130_) {
        ItemStack itemstack = p_41129_.getItemInHand(p_41130_);
        p_41128_.playSound(
            null,
            p_41129_.getX(),
            p_41129_.getY(),
            p_41129_.getZ(),
            SoundEvents.EGG_THROW,
            SoundSource.PLAYERS,
            0.5F,
            0.4F / (p_41128_.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        if (!p_41128_.isClientSide) {
            ThrownEgg thrownegg = new ThrownEgg(p_41128_, p_41129_);
            thrownegg.setItem(itemstack);
            thrownegg.shootFromRotation(p_41129_, p_41129_.getXRot(), p_41129_.getYRot(), 0.0F, 1.5F, 1.0F);
            p_41128_.addFreshEntity(thrownegg);
        }

        p_41129_.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, p_41129_);
        return InteractionResultHolder.sidedSuccess(itemstack, p_41128_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338884_, Position p_338312_, ItemStack p_338704_, Direction p_338366_) {
        ThrownEgg thrownegg = new ThrownEgg(p_338884_, p_338312_.x(), p_338312_.y(), p_338312_.z());
        thrownegg.setItem(p_338704_);
        return thrownegg;
    }
}
