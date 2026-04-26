package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public class ArrowItem extends Item implements ProjectileItem {
    public ArrowItem(Item.Properties p_40512_) {
        super(p_40512_);
    }

    public AbstractArrow createArrow(Level p_40513_, ItemStack p_40514_, LivingEntity p_40515_, @Nullable ItemStack p_344832_) {
        return new Arrow(p_40513_, p_40515_, p_40514_.copyWithCount(1), p_344832_);
    }

    @Override
    public Projectile asProjectile(Level p_338330_, Position p_338329_, ItemStack p_338197_, Direction p_338469_) {
        Arrow arrow = new Arrow(p_338330_, p_338329_.x(), p_338329_.y(), p_338329_.z(), p_338197_.copyWithCount(1), null);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }

    /**
     * Called to determine if this arrow will be infinite when fired. If an arrow is infinite, then the arrow will never be consumed (regardless of enchantments).
     * <p>
     * Only called on the logical server.
     *
     * @param ammo The ammo stack (containing this item)
     * @param bow  The bow stack
     * @param livingEntity The entity who is firing the bow
     * @return True if the arrow is infinite
     */
    public boolean isInfinite(ItemStack ammo, ItemStack bow, net.minecraft.world.entity.LivingEntity livingEntity) {
        return false;
    }
}
