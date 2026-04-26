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
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.Level;

public class ExperienceBottleItem extends Item implements ProjectileItem {
    public ExperienceBottleItem(Item.Properties p_41194_) {
        super(p_41194_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_41196_, Player p_41197_, InteractionHand p_41198_) {
        ItemStack itemstack = p_41197_.getItemInHand(p_41198_);
        p_41196_.playSound(
            null,
            p_41197_.getX(),
            p_41197_.getY(),
            p_41197_.getZ(),
            SoundEvents.EXPERIENCE_BOTTLE_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (p_41196_.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        if (!p_41196_.isClientSide) {
            ThrownExperienceBottle thrownexperiencebottle = new ThrownExperienceBottle(p_41196_, p_41197_);
            thrownexperiencebottle.setItem(itemstack);
            thrownexperiencebottle.shootFromRotation(p_41197_, p_41197_.getXRot(), p_41197_.getYRot(), -20.0F, 0.7F, 1.0F);
            p_41196_.addFreshEntity(thrownexperiencebottle);
        }

        p_41197_.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, p_41197_);
        return InteractionResultHolder.sidedSuccess(itemstack, p_41196_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338868_, Position p_338766_, ItemStack p_338321_, Direction p_338772_) {
        ThrownExperienceBottle thrownexperiencebottle = new ThrownExperienceBottle(p_338868_, p_338766_.x(), p_338766_.y(), p_338766_.z());
        thrownexperiencebottle.setItem(p_338321_);
        return thrownexperiencebottle;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
